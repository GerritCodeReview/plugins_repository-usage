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
