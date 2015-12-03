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
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.internal.UniqueAnnotations;

public class Module extends AbstractModule {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
        .to(EventHandler.class);
    requestStaticInjection(Config.class);
    requestStaticInjection(Ref.Table.class);
    requestStaticInjection(Usage.Table.class);
    install(new FactoryModuleBuilder()
        .implement(RefUpdateHandler.class, RefUpdateHandlerImpl.class)
        .build(RefUpdateHandlerFactory.class));
    bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create())
        .to(SQLDriver.class);
  }

  @Provides
  @Singleton
  SQLDriver provideSqlDriver() {
    return new SQLDriver();
  }
}
