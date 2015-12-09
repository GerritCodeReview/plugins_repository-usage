// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.repositoryuse;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefUpdateHandlerImpl implements RefUpdateHandler {
  private static final Logger log =
      LoggerFactory.getLogger(RefUpdateHandlerImpl.class);

  private RefUpdate event;
  private final GitRepositoryManager repoManager;
  private final String serverName;

  @Inject
  public RefUpdateHandlerImpl(@Assisted RefUpdate event,
      GitRepositoryManager repoManager,
      @CanonicalWebUrl String canonicalWebUrl) {
    this.event = event;
    this.repoManager = repoManager;
    if (canonicalWebUrl != null) {
      try {
        URL url = new URL(canonicalWebUrl);
        canonicalWebUrl = url.getHost();
      } catch (MalformedURLException e) {
        log.warn("Could not parse canonicalWebUrl", e);
      }
    }
    this.serverName = canonicalWebUrl;
  }

  @Override
  public void run() {
    if (event.isDelete() && event.getRefName().startsWith(Constants.R_HEADS)
        || event.getRefName().startsWith(Constants.R_TAGS)) {
      // Ref was deleted... clean up any references
      Ref ref = Ref.fetchByRef(getCanonicalProject(event.getProjectName()),
          event.getRefName());
      if (ref != null) {
        ref.delete();
      }
      if (event.getRefName().startsWith(Constants.R_HEADS)) {
        // Also clean up uses from this ref
        Usage.deleteByBranch(getCanonicalProject(event.getProjectName()),
            event.getRefName());
      }
    } else if (event.getRefName().startsWith(Constants.R_TAGS)) {
      Ref updatedRef = new Ref(getCanonicalProject(event.getProjectName()),
          event.getRefName(), event.getNewObjectId());
      updatedRef.save();
    } else if (event.getRefName().startsWith(Constants.R_HEADS)) {
      Ref updatedRef = new Ref(getCanonicalProject(event.getProjectName()),
          event.getRefName(), event.getNewObjectId());
      updatedRef.save();
      Project.NameKey nameKey = new Project.NameKey(event.getProjectName());
      try {
        if (Config.refreshAllSubmodules() || event.isCreate()
            || isSubmoduleUpdate(event, nameKey)) {
          Map<String, String> submodules = getSubmodules(event, nameKey);
          updateProjects(event.getProjectName(), event.getRefName(),
              submodules);
        }
        if (Config.parseManifests()) {
          parseManifests(event, nameKey);
        }
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  private void parseManifests(RefUpdate event, Project.NameKey project)
      throws RepositoryNotFoundException, IOException {
    if (event.isDelete()) {
      return;
    }
    try (Repository repo = repoManager.openRepository(project)) {
      try (RevWalk walk = new RevWalk(repo); TreeWalk tw = new TreeWalk(repo)) {
        RevCommit commit =
            walk.parseCommit(repo.resolve(event.getNewObjectId()));

        tw.setRecursive(false);
        tw.addTree(commit.getTree());
        ObjectReader or = tw.getObjectReader();
        while (tw.next()) {
          String path = tw.getPathString();
          if (path.endsWith(".xml")) {
            ManifestParser mp = new ManifestParser();
            ObjectLoader ol = or.open(tw.getObjectId(0));
            if (!ol.isLarge()) {
              Map<String, String> tmp = mp.parseManifest(ol.getBytes());
              HashMap<String, String> projects = new HashMap<>();
              for (String key : tmp.keySet()) {
                projects
                    .put(
                        normalizePath(String.format("%s:%s",
                            event.getProjectName(), path), key, true),
                    tmp.get(key));
              }
              updateProjects(
                  String.format("%s:%s", event.getProjectName(), path),
                  event.getRefName(), projects);
            } else {
              log.warn(String.format(
                  "project: %s, branch: %s, file: %s is too large, "
                      + "skipping manifest parse",
                  event.getProjectName(), event.getRefName(),
                  tw.getPathString()));
            }
          }
        }
      }
    }
  }

  /**
   * Has a submodule been updated?
   *
   * @param event the Event
   * @return True if a submodule update occurred, otherwise False.
   */
  private boolean isSubmoduleUpdate(RefUpdate event, Project.NameKey project)
      throws RepositoryNotFoundException, IOException {
    if (event.isDelete()) {
      return false;
    }
    try (Repository repo = repoManager.openRepository(project)) {
      try (RevWalk walk = new RevWalk(repo);
          DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        RevTree aTree = null;
        if (!event.isCreate()) {
          // If this is a new ref, we can't get the original commit.
          // We can still use the DiffFormatter to give us what changed
          // by passing null, however.
          RevCommit aCommit =
              walk.parseCommit(repo.resolve(event.getOldObjectId()));
          aTree = aCommit.getTree();
        }
        RevCommit bCommit =
            walk.parseCommit(repo.resolve(event.getNewObjectId()));
        RevTree bTree = bCommit.getTree();

        df.setRepository(repo);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        df.setDetectRenames(true);
        List<DiffEntry> diffEntries = df.scan(aTree, bTree);
        for (DiffEntry de : diffEntries) {
          FileMode oldMode = de.getOldMode();
          FileMode newMode = de.getNewMode();
          if ((oldMode != null && oldMode == FileMode.GITLINK)
              || (newMode != null && newMode == FileMode.GITLINK)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private Map<String, String> getSubmodules(RefUpdate event,
      Project.NameKey project) throws RepositoryNotFoundException, IOException {
    HashMap<String, String> submodules = new HashMap<>();
    try (Repository repo = repoManager.openRepository(project)) {
      try (RevWalk walk = new RevWalk(repo);
          SubmoduleWalk sw = new SubmoduleWalk(repo);
          TreeWalk cw = new TreeWalk(repo)) {
        org.eclipse.jgit.lib.Config modulesConfig = null;

        // TODO: Nasty hack! Work around JGit bug where modules aren't
        // found if path is not the same as the name in the config!
        // Also, BlobBasedConfig (which is used by SubmoduleWalk) doesn't
        // handle UTF-8 BOMs, so we need to do some massaging.
        RevCommit commit =
            walk.parseCommit(repo.resolve(event.getNewObjectId()));
        cw.addTree(commit.getTree());
        cw.setRecursive(false);
        PathFilter filter = PathFilter.create(Constants.DOT_GIT_MODULES);
        cw.setFilter(filter);
        while (cw.next()) {
          if (filter.isDone(cw)) {
            ObjectReader reader = repo.newObjectReader();
            String decoded = "";
            try {
              ObjectLoader loader =
                  reader.open(cw.getObjectId(0), Constants.OBJ_BLOB);
              byte[] configBytes = loader.getCachedBytes(Integer.MAX_VALUE);
              if (configBytes.length >= 3 && configBytes[0] == (byte) 0xEF
                  && configBytes[1] == (byte) 0xBB
                  && configBytes[2] == (byte) 0xBF) {
                decoded = RawParseUtils.decode(RawParseUtils.UTF8_CHARSET,
                    configBytes, 3, configBytes.length);
              } else {
                decoded = RawParseUtils.decode(configBytes);
              }
            } catch (IOException e) {
              log.error(
                  String.format("Unable to load .gitmodules in %s branch %s",
                      event.getProjectName(), event.getRefName()),
                  e);
            }
            modulesConfig = new org.eclipse.jgit.lib.Config();
            modulesConfig.fromText(decoded);
          }
        }
        sw.setTree(commit.getTree());
        sw.setRootTree(commit.getTree());
        sw.setModulesConfig(modulesConfig);
        while (sw.next()) {
          String modulesUrl = sw.getModulesUrl();
          if (modulesUrl == null && modulesConfig != null) {
            for (String key : modulesConfig
                .getSubsections(ConfigConstants.CONFIG_SUBMODULE_SECTION)) {
              if (sw.getPath()
                  .equals(modulesConfig.getString(
                      ConfigConstants.CONFIG_SUBMODULE_SECTION, key,
                      ConfigConstants.CONFIG_KEY_PATH))) {
                modulesUrl = modulesConfig.getString(
                    ConfigConstants.CONFIG_SUBMODULE_SECTION, key,
                    ConfigConstants.CONFIG_KEY_URL);
                break;
              }
            }
          }
          if (modulesUrl != null) {
            submodules.put(
                normalizePath(event.getProjectName(), modulesUrl, false),
                sw.getObjectId().name());
          } else {
            log.warn(String.format(
                "invalid .gitmodules in %s %s configuration: missing url for %s",
                event.getProjectName(), event.getRefName(), sw.getPath()));
          }
        }
      } catch (ConfigInvalidException e) {
        log.warn(String.format(
            "Invalid .gitmodules configuration while parsing %s branch %s",
            event.getProjectName(), event.getRefName()), e);
      }
    }
    return submodules;
  }

  private void updateProjects(String project, String branch,
      Map<String, String> projects) {
    String canonicalProject = getCanonicalProject(project);
    List<Usage> uses = Usage.fetchByProject(canonicalProject, branch);
    for (Usage use : uses) {
      if (!projects.containsKey(use.getDestination())) {
        // No longer exists; delete.
        use.delete();
      } else {
        // Update SHA1 here.
        use.setRef(projects.get(use.getDestination()));
        use.save();
        projects.remove(use.getDestination());
      }
    }
    // At this point, submodules only contains new elements.
    // Create them.
    for (String key : projects.keySet()) {
      Usage use = new Usage(canonicalProject, branch, key, projects.get(key));
      use.save();
    }
  }

  private String getCanonicalProject(String project) {
    String canonicalProject =
        String.format("https://%s/%s", serverName, project);
    try {
      URL url = new URL(canonicalProject);
      canonicalProject = url.getHost() + url.getPath();
    } catch (MalformedURLException e) {
      log.warn("Could not parse project as URL: " + canonicalProject);
    }
    return canonicalProject;
  }

  private String normalizePath(String project, String destination,
      boolean isManifest) {
    String originalProject =
        isManifest ? project.substring(0, project.lastIndexOf(":")) : project;

    // Strip trailing slashes and .git suffix
    if (destination.endsWith("/")) {
      destination = destination.substring(0, destination.length() - 1);
    }

    if (destination.endsWith(".git")) {
      destination = destination.substring(0, destination.length() - 4);
    }

    // Handle relative and absolute paths on the same server
    if (destination.startsWith("//")) {
      // UNC path; let this pass through unaltered.
      // This should be rather uncommon, though.
      return destination;
    }
    if (destination.startsWith("/")) {
      if (serverName != null) {
        destination = serverName + destination;
      } else {
        log.warn("Could not parse absolute path; canonicalWebUrl not set");
      }
    } else if (destination.startsWith(".")) {
      if (serverName != null) {
        Path path = Paths.get(String.format("/%s/%s", project, destination));
        destination = serverName + path.normalize().toString();
      } else {
        log.warn("Could not parse relative path; canonicalWebUrl not set");
      }
    } else if (!destination.matches("^[^:]+://.*")) {
      if (serverName != null) {
        destination = serverName + "/" + originalProject + "/" + destination;
      } else {
        log.warn("Could not parse relative path; canonicalWebURl not set");
      }
    }

    try {
      // Replace the protocol with a known scheme, to avoid angering URL
      destination = destination.replaceFirst("^[^:]+://", "");
      URL url = new URL("https://" + destination);
      destination = url.getHost();
      Path path = Paths.get(url.getPath()).normalize();
      destination += path.toString();
    } catch (MalformedURLException e) {
      log.warn("Could not parse destination as URL: " + destination);
    }
    return destination;
  }
}
