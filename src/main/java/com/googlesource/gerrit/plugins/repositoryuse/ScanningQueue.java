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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ScanningQueue implements LifecycleListener {
  private final WorkQueue queue;
  private WorkQueue.Executor threadPool;

  @Inject
  public ScanningQueue(WorkQueue queue) {
    this.queue = queue;
  }

  @Override
  public void start() {
    threadPool = queue.createQueue(1, "(Repository-Usage)");
  }

  @Override
  public void stop() {
    if (threadPool != null) {
      threadPool = null;
    }
  }

  public ScheduledThreadPoolExecutor getPool() {
    return threadPool;
  }

}
