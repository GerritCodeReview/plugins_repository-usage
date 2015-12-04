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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

public class Config {
  public enum Database {
    H2, POSTGRESQL
  }

  @Inject
  private static PluginConfigFactory cfg;

  @Inject
  @PluginName
  private static String pluginName;

  @Inject
  private static SitePaths sitePaths;

  private static boolean configParsed = false;
  private static boolean refreshAllSubmodules;
  private static boolean parseManifests;
  private static Database databaseType;
  private static String database;
  private static String databaseHost;
  private static String databaseUser;
  private static String databasePassword;

  private static void readConfig() {
    PluginConfig pc = cfg.getFromGerritConfig(pluginName);
    refreshAllSubmodules = pc.getBoolean("refreshAllSubmodules", false);
    parseManifests = pc.getBoolean("parseManifests", true);
    databaseType = pc.getEnum("databaseType", Database.H2);
    database = pc.getString("database",
        sitePaths.site_path.toPath().resolve("db/UsageDB").toString());
    databaseHost = pc.getString("databaseHost", "");
    databaseUser = pc.getString("databaseUser", "");
    databasePassword = pc.getString("databasePassword", "");
    configParsed = true;
  }

  public static boolean refreshAllSubmodules() {
    if (!configParsed) {
      readConfig();
    }
    return refreshAllSubmodules;
  }

  public static boolean parseManifests() {
    if (!configParsed) {
      readConfig();
    }
    return parseManifests;
  }

  public static Database getDatabaseType() {
    if (!configParsed) {
      readConfig();
    }
    return databaseType;
  }

  public static String getDatabase() {
    if (!configParsed) {
      readConfig();
    }
    return database;
  }

  public static String getDatabaseHost() {
    if (!configParsed) {
      readConfig();
    }
    return databaseHost;
  }

  public static String getDatabaseUser() {
    if (!configParsed) {
      readConfig();
    }
    return databaseUser;
  }

  public static String getDatabasePassword() {
    if (!configParsed) {
      readConfig();
    }
    return databasePassword;
  }
}
