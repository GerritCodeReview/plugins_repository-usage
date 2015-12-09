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

import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ScanTaskImpl implements ScanTask {
  private static final Logger log =
      LoggerFactory.getLogger(RefUpdateHandlerImpl.class);

  private String project;
  private String branch;
  private RefUpdateHandlerFactory refUpdateHandlerFactory;
  private Projects projects;

  @AssistedInject
  public ScanTaskImpl(@Assisted String project,
      RefUpdateHandlerFactory refUpdateHandlerFactory,
      Projects projects) {
    init(project, null, refUpdateHandlerFactory, projects);
  }

  @AssistedInject
  public ScanTaskImpl(@Assisted("project") String project,
      @Assisted("branch") String branch,
      RefUpdateHandlerFactory refUpdateHandlerFactory,
      Projects projects) {
    init(project, branch, refUpdateHandlerFactory, projects);
  }

  private void init(String project, String branch,
      RefUpdateHandlerFactory refUpdateHandlerFactory,
      Projects projects) {
    this.project = project;
    this.branch = branch;
    this.refUpdateHandlerFactory = refUpdateHandlerFactory;
    this.projects = projects;
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
    List<BranchInfo> branches = null;
    try {
      branches = projects.name(project).branches().get();
    } catch (RestApiException e) {
      log.error(e.getMessage(), e);
    }
    if (!branch.startsWith(Constants.R_HEADS)) {
      branch = Constants.R_HEADS.length() + branch;
    }
    for (BranchInfo currentBranch : branches) {
      // Create with a "new" base commit to rescan entire branch
      if (branch == null || branch == currentBranch.ref) {
        RefUpdate rescan = new RefUpdate(project, currentBranch.ref,
            ObjectId.zeroId().getName(), currentBranch.revision);
        try {
          refUpdateHandlerFactory.create(rescan).run();
        } catch (Exception e) {
          log.error(String.format("Error updating %s branch %s: %s", project,
              branch, e.getMessage()), e);
        }
      }
    }
  }

}
