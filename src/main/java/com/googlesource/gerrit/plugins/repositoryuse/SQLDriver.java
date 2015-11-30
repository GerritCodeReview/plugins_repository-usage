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

import com.googlesource.gerrit.plugins.repositoryuse.Config.Database;

import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQLDriver {
  private static final Logger log =
      LoggerFactory.getLogger(ManifestParser.class);
  private static final int POOL_SIZE = 5;
  private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private BasicDataSource ds;

  public SQLDriver() {
    ds = new BasicDataSource();
    try {
      ds.setDriverClassName(getDriver());
      ds.setUrl(getDatabaseUrl());
      ds.setUsername(Config.getDatabaseUser());
      ds.setPassword(Config.getDatabasePassword());
      ds.setInitialSize(POOL_SIZE);
    } catch (Exception e) {
      log.error("Unable to create database connection", e);
    }
  }

  public List<Map<String, String>> fetchRows(String query, String...parameters) throws SQLException {
    ArrayList<Map<String, String>> result = new ArrayList<>();
    try (Connection c = ds.getConnection();
        PreparedStatement s = c.prepareStatement(query)) {
      int i=1;
      for (String param : parameters) {
        s.setString(i, param);
        i++;
      }
      ResultSet r = s.executeQuery();
      ResultSetMetaData rsmd = r.getMetaData();
      while (r.next()) {
        HashMap<String, String> row = new HashMap<>(rsmd.getColumnCount());
        for (i=1; i<=rsmd.getColumnCount(); i++) {
          row.put(rsmd.getColumnLabel(i).toLowerCase(), r.getString(i));
        }
        result.add(row);
      }
    }
    return result;
  }

  public void execute(String query, String...parameters) throws SQLException {
    try (Connection c = ds.getConnection();
        PreparedStatement s = c.prepareStatement(query)) {
      int i=1;
      for (String param : parameters) {
        s.setString(i, param);
        i++;
      }
      if (!s.execute() && s.getUpdateCount() > 0) {
        if (c.getAutoCommit() == false) {
          c.commit();
        }
      }
    }
  }

  public String getDateAsString(Date date) {
    if (date != null) {
      return sdf.format(date);
    }
    return sdf.format(new Date());
  }

  public Date getStringAsDate(String date) {
    if (date != null) {
      try {
        return sdf.parse(date);
      } catch (ParseException e) {
        log.warn("Unable to parse date", e);
      }
    }
    return new Date();
  }

  public String getDateFormat() {
    if (Config.getDatabaseType() == Database.POSTGRESQL) {
      return "TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS')";
    }
    return "?";
  }

  private String getDriver() throws Exception {
    if (Config.getDatabaseType() == Database.H2) {
      return "org.h2.Driver";
    } else if (Config.getDatabaseType() == Database.POSTGRESQL) {
      return "org.postgresql.Driver";
    }
    throw new Exception("Unsupported database engine");
  }

  private String getDatabaseUrl() throws Exception {
    if (Config.getDatabaseType() == Database.H2) {
      return "jdbc:h2:" + Config.getDatabase();
    } else if (Config.getDatabaseType() == Database.POSTGRESQL) {
      return "jdbc:postgresql://" + Config.getDatabaseHost() + "/"+ Config.getDatabase();
    }
    throw new Exception("Unsupported database engine");
  }

}
