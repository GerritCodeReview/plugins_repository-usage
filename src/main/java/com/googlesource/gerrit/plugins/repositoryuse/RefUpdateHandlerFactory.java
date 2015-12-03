package com.googlesource.gerrit.plugins.repositoryuse;

public interface RefUpdateHandlerFactory {
  RefUpdateHandler create(RefUpdate update);
}
