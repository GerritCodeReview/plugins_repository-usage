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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public class ScanTaskImpl implements ScanTask {
  private static final Logger log =
      LoggerFactory.getLogger(RefUpdateHandlerImpl.class);

  private String project;
  private String branch;
  private RefUpdateHandlerFactory refUpdateHandlerFactory;
  private ProjectControl.GenericFactory projectControl;
  private CurrentUser user;
  private GitRepositoryManager repoManager;

  @AssistedInject
  public ScanTaskImpl(@Assisted String project,
      RefUpdateHandlerFactory refUpdateHandlerFactory,
      ProjectControl.GenericFactory p, CurrentUser user,
      GitRepositoryManager repoManager) {
    init(project, null, refUpdateHandlerFactory, p, user, repoManager);
  }

  @AssistedInject
  public ScanTaskImpl(@Assisted("project") String project,
      @Assisted("branch") String branch,
      RefUpdateHandlerFactory refUpdateHandlerFactory,
      ProjectControl.GenericFactory p, CurrentUser user,
      GitRepositoryManager repoManager) {
    init(project, branch, refUpdateHandlerFactory, p, user, repoManager);
  }

  private void init(String project, String branch,
      RefUpdateHandlerFactory refUpdateHandlerFactory,
      ProjectControl.GenericFactory p, CurrentUser user,
      GitRepositoryManager repoManager) {
    this.project = project;
    this.branch = branch;
    this.refUpdateHandlerFactory = refUpdateHandlerFactory;
    this.projectControl = p;
    this.user = user;
    this.repoManager = repoManager;
  }

  @Override
  public String toString() {
    if (branch != null) {
      return String.format("(repository-usage) scan %s branch %s", project,
          branch);
    }
    return String.format("(repository-usage) scan %s", project);
  }

  @Override
  public void run() {
    Map<String, org.eclipse.jgit.lib.Ref> branches = null;
    Project.NameKey nameKey = new Project.NameKey(project);
    try {
      ProjectResource project =
          new ProjectResource(projectControl.controlFor(nameKey, user));
      try (Repository repo = repoManager.openRepository(project.getNameKey())) {
        branches = repo.getRefDatabase().getRefs(Constants.R_HEADS);
      }
    } catch (NoSuchProjectException | IOException e) {
      log.error(e.getMessage(), e);
    }
    if (branch == null) {
      for (String currentBranch : branches.keySet()) {
        // Create with a "new" base commit to rescan entire branch
        RefUpdate rescan = new RefUpdate(project,
            branches.get(currentBranch).getName(), ObjectId.zeroId().getName(),
            branches.get(currentBranch).getObjectId().name());
        try {
          refUpdateHandlerFactory.create(rescan).run();
        } catch (Exception e) {
          log.error(String.format("Error updating %s branch %s: %s", project,
              branch, e.getMessage()), e);
        }
      }
    } else {
      if (branch.startsWith(Constants.R_HEADS)) {
        branch = branch.substring(Constants.R_HEADS.length());
      }

      if (branches.containsKey(branch)) {
        RefUpdate rescan = new RefUpdate(project,
            branches.get(branch).getName(), ObjectId.zeroId().getName(),
            branches.get(branch).getObjectId().name());
        try {
          refUpdateHandlerFactory.create(rescan).run();
        } catch (Exception e) {
          log.error(String.format("Error updating %s branch %s: %s", project,
              branch, e.getMessage()), e);
        }
      } else {
        log.warn(String.format("Branch %s does not exist; skipping", branch));
      }
    }
  }

}
