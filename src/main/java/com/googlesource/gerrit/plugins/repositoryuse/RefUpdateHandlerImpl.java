package com.googlesource.gerrit.plugins.repositoryuse;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
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
  private final ProjectControl.GenericFactory projectControl;
  private final CurrentUser user;
  private final GitRepositoryManager repoManager;
  private final String serverName;

  @Inject
  public RefUpdateHandlerImpl(@Assisted RefUpdate event,
      ProjectControl.GenericFactory p,
      CurrentUser user,
      GitRepositoryManager repoManager,
      @CanonicalWebUrl String canonicalWebUrl) {
    this.event = event;
    this.projectControl = p;
    this.user = user;
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
      Ref ref = Ref.fetchByRef(event.getProjectName(), event.getRefName());
      if (ref != null) {
        ref.delete();
      }
      if (event.getRefName().startsWith(Constants.R_HEADS)) {
        // Also clean up uses from this ref
        Usage.deleteByBranch(getCanonicalProject(event.getProjectName()), event.getRefName());
      }
    } else if (event.getRefName().startsWith(Constants.R_TAGS)) {
      Ref updatedRef = new Ref(event.getProjectName(), event.getRefName(), event.getNewObjectId());
      updatedRef.save();
    } else if (event.getRefName().startsWith(Constants.R_HEADS)) {
      Ref updatedRef = new Ref(event.getProjectName(), event.getRefName(), event.getNewObjectId());
      updatedRef.save();
      Project.NameKey nameKey = new Project.NameKey(event.getProjectName());
      try {
        ProjectResource project =
            new ProjectResource(projectControl.controlFor(nameKey, user));
        if (Config.refreshAllSubmodules() || event.isCreate() || isSubmoduleUpdate(event, project)) {
          Map<String, String> submodules = getSubmodules(event, project);
          updateProjects(event.getProjectName(), event.getRefName(), submodules);
        }
        if (Config.parseManifests()) {
          parseManifests(event, project);
        }
      } catch (NoSuchProjectException | IOException e) {
        log.error(e.getMessage(), e);
      }
    }
  }

  private void parseManifests(RefUpdate event, ProjectResource project) throws RepositoryNotFoundException, IOException {
    if (event.isDelete()) {
      return;
    }
    try (Repository repo = repoManager.openRepository(project.getNameKey())) {
      try (RevWalk walk = new RevWalk(repo);
          TreeWalk tw = new TreeWalk(repo)) {
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
                projects.put(normalizePath(String.format("%s:%s",  event.getProjectName(), path), key, true), tmp.get(key));
              }
              updateProjects(String.format("%s:%s", event.getProjectName(), path), event.getRefName(), projects);
            } else {
              log.warn(String.format("%s is too large, skipping manifest parse", tw.getPathString()));
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
  private boolean isSubmoduleUpdate(RefUpdate event, ProjectResource project)
      throws RepositoryNotFoundException, IOException {
    if (event.isDelete()) {
      return false;
    }
    try (Repository repo = repoManager.openRepository(project.getNameKey())) {
      try (RevWalk walk = new RevWalk(repo);
          DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        RevTree aTree = null;
        if (!event.isCreate()) {
          // If this is a new ref, we can't get the original commit.
          // We can still use the DiffFormatter to give us what changed
          // by passing null, however.
          RevCommit aCommit = walk.parseCommit(repo.resolve(event.getOldObjectId()));
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
              || (newMode != null && newMode == FileMode.GITLINK))
          {
            return true;
          }
        }
      }
    }
    return false;
  }

  private Map<String,String> getSubmodules(RefUpdate event, ProjectResource project)
      throws RepositoryNotFoundException, IOException {
    HashMap<String, String> submodules = new HashMap<>();
    try (Repository repo = repoManager.openRepository(project.getNameKey())) {
      try (RevWalk walk = new RevWalk(repo);
          SubmoduleWalk sw = new SubmoduleWalk(repo)) {
        RevCommit commit =
            walk.parseCommit(repo.resolve(event.getNewObjectId()));
        sw.setTree(commit.getTree());
        sw.setRootTree(commit.getTree());
        while (sw.next()) {
          submodules.putIfAbsent(normalizePath(project.getName(), sw.getModulesUrl(), false), sw.getObjectId().name());
        }
      } catch (ConfigInvalidException e) {
        log.warn("Invalid .gitmodules configuration while parsing "
            + project.getName());
      }
    }
    return submodules;
  }

  private void updateProjects(String project, String branch, Map<String, String> projects) {
    String canonicalProject = getCanonicalProject(project);
    List<Usage> uses = Usage.fetchByProject(canonicalProject);
    for(Usage use : uses) {
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
    for(String key : projects.keySet()) {
      Usage use = new Usage(canonicalProject, branch, key, projects.get(key));
      use.save();
    }
  }

  private String getCanonicalProject(String project) {
    String canonicalProject = String.format("https://%s/%s", serverName, project);
    try {
      URL url = new URL(canonicalProject);
      canonicalProject = url.getHost() + url.getPath();
    } catch (MalformedURLException e) {
      log.warn("Could not parse project as URL: " + canonicalProject);
    }
    return canonicalProject;
  }

  private String normalizePath(String project, String destination, boolean isManifest) {
    String originalProject = isManifest ? project.substring(0, project.lastIndexOf(":")) : project;

    // Handle relative and absolute paths on the same server
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
      destination = url.getHost() + url.getPath();
    } catch (MalformedURLException e) {
      log.warn("Could not parse destination as URL: " + destination);
    }
    return destination;
  }
}
