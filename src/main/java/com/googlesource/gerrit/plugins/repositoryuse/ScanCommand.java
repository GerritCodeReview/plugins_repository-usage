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

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.extensions.annotations.CapabilityScope;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@RequiresCapability(value = "administrateServer", scope = CapabilityScope.CORE)
@CommandMetaData(name = "scan", description = "Scan specific projects or branches")
final class ScanCommand extends SshCommand {
  @Option(name = "--all", usage = "push all known projects")
  private boolean all;

  @Option(name = "--branch", metaVar = "BRANCH", usage = "branches to scan")
  private String[] branches;

  @Argument(index = 0, multiValued = true, metaVar = "PROJECT", usage = "project name pattern")
  private List<String> projects = new ArrayList<>(2);

  private final ScanTaskFactory scanTaskFactory;
  private final ScheduledThreadPoolExecutor pool;
  private final ProjectCache projectCache;


  @Inject
  public ScanCommand(ScanTaskFactory scanTaskFactory,
      @ScanningPool ScheduledThreadPoolExecutor pool,
      ProjectCache projectCache) {
    this.scanTaskFactory = scanTaskFactory;
    this.pool = pool;
    this.projectCache = projectCache;
  }

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    if (all && projects.size() > 0) {
      throw new UnloggedFailure(1, "error: cannot combine --all and PROJECT");
    }

    if (all) {
      for (NameKey project : projectCache.all()) {
        projects.add(project.get());
      }
    }

    for (String project : projects) {
      if (branches == null || branches.length == 0) {
        pool.execute(scanTaskFactory.create(project));
      } else {
        for (String branch : branches) {
          pool.execute(scanTaskFactory.create(project, branch));
        }
      }
    }
  }

}
