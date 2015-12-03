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

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;

import org.eclipse.jgit.lib.ObjectId;

public class RefUpdate {
  private String projectName;
  private String refName;
  private String oldObjectId;
  private String newObjectId;
  private boolean isCreate;
  private boolean isDelete;

  public RefUpdate(String projectName, String refName, String oldObjectId,
      String newObjectId) {
    this.projectName = projectName;
    this.refName = refName;
    this.oldObjectId = oldObjectId;
    this.newObjectId = newObjectId;
    this.isCreate = oldObjectId.equals(ObjectId.zeroId().name());
    this.isDelete = newObjectId.equals(ObjectId.zeroId().name());
  }

  public RefUpdate(GitReferenceUpdatedListener.Event event) {
    this.projectName = event.getProjectName();
    this.refName = event.getRefName();
    this.oldObjectId = event.getOldObjectId();
    this.newObjectId = event.getNewObjectId();
    this.isCreate = event.isCreate();
    this.isDelete = event.isDelete();
  }

  public String getProjectName() {
    return projectName;
  }

  public String getRefName() {
    return refName;
  }

  public String getOldObjectId() {
    return oldObjectId;
  }

  public String getNewObjectId() {
    return newObjectId;
  }

  public boolean isCreate() {
    return isCreate;
  }

  public boolean isDelete() {
    return isDelete;
  }
}
