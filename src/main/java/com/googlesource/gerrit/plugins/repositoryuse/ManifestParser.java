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

import org.apache.commons.digester3.Digester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ManifestParser {
  private static final Logger log =
      LoggerFactory.getLogger(ManifestParser.class);
  private HashMap<String, String> remotes;
  private Project defaultProject;
  private ArrayList<Project> projects;

  public ManifestParser() {
    remotes = new HashMap<>();
    defaultProject = new Project(null, null, null);
    projects = new ArrayList<>();
  }

  public Map<String, String> parseManifest(byte[] contents) {
    Digester digester = new Digester();
    digester.push(this);

    // Add all remote handlers
    digester.addCallMethod("manifest/remote", "addRemote", 2);
    digester.addCallParam("manifest/remote", 0, "name");
    digester.addCallParam("manifest/remote", 1, "fetch");

    // Add all default handlers.  This handles both repo format
    // attributes (remote, revision) and non-standard attributes
    // (branch, tag, commit-id).
    digester.addCallMethod("manifest/default", "addDefault", 5);
    digester.addCallParam("manifest/default", 0, "remote");
    digester.addCallParam("manifest/default", 1, "revision");
    digester.addCallParam("manifest/default", 2, "branch");
    digester.addCallParam("manifest/default", 3, "tag");
    digester.addCallParam("manifest/default", 4, "commit-id");

    // Add all project handlers.  This handles both repo format
    // attributes (remote, revision) and non-standard attributes
    // (branch, tag, commit-id).
    digester.addCallMethod("manifest/project", "addProject", 6);
    digester.addCallParam("manifest/project", 0, "remote");
    digester.addCallParam("manifest/project", 1, "name");
    digester.addCallParam("manifest/project", 2, "revision");
    digester.addCallParam("manifest/project", 3, "branch");
    digester.addCallParam("manifest/project", 4, "tag");
    digester.addCallParam("manifest/project", 5, "commit-id");


    InputStream input = new ByteArrayInputStream(contents);
    try {
      digester.parse(input);
    } catch (IOException | SAXException e) {
      log.warn("Unable to parse manifest", e);
    }
    HashMap<String, String> resolvedProjects = new HashMap<>(projects.size());
    for (Project p : projects) {
      String uri = null;
      String revision = null;

      if (p.getRemote() != null) {
        uri = remotes.get(p.getRemote());
      }

      if (uri == null && defaultProject.getRemote() != null) {
        uri = remotes.get(defaultProject.getRemote());
      }

      if (uri == null && p.getRemote() != null) {
        uri = p.getRemote();
      }

      if (uri != null) {
        uri += "/" + p.getName();
      }

      if (p.getRevision() != null) {
        revision = p.getRevision();
      } else if (defaultProject.getRevision() != null) {
        revision = defaultProject.getRevision();
      }

      if (uri != null && revision != null) {
        resolvedProjects.putIfAbsent(uri, revision);
      } else {
        log.warn("Invalid project description in manifest");
      }
    }
    return resolvedProjects;
  }

  public void addRemote(String name, String fetch) {
    remotes.putIfAbsent(name, fetch);
  }

  public void addDefault(String remote, String revision, String branch, String tag, String commitId) {
    defaultProject = new Project(remote, null, getRevision(revision, branch, tag, commitId));
  }

  public void addProject(String remote, String name, String revision, String branch, String tag, String commitId) {
    if (name != null) {
      projects.add(new Project(remote, name, getRevision(revision, branch, tag, commitId)));
    } else {
      log.warn("Project name not specified in manifest");
    }
  }

  private String getRevision(String revision, String branch, String tag, String commitId) {
    if (revision != null) {
      return revision;
    } else if (branch != null) {
      return branch;
    } else if (tag != null) {
      return tag;
    } else if (commitId != null) {
      return commitId;
    }
    return null;
  }

  public static class Project {
    private String remote;
    private String name;
    private String revision;

    public Project(String remote, String name, String revision) {
      this.remote = remote;
      this.name = name;
      this.revision = revision;
    }

    public String getRemote() {
      return remote;
    }

    public String getName() {
      return name;
    }

    public String getRevision() {
      return revision;
    }
  }

}
