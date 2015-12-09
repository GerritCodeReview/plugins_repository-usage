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

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Usage {
  private static final Logger log = LoggerFactory.getLogger(Usage.class);
  private static Table table = new Table();


  private String project;
  private String branch;
  private String destination;
  private String ref;
  private String info;
  private Date lastUpdated;

  public Usage(String project, String branch, String destination, String ref) {
    init(project, branch, destination, ref, null, new Date());
  }

  public Usage(String project, String branch, String destination, String ref,
      String info) {
    init(project, branch, destination, ref, info, new Date());
  }

  public Usage(String project, String branch, String destination, String ref,
      String info, Date date) {
    init(project, branch, destination, ref, info, date);
  }

  private void init(String project, String branch, String destination,
      String ref, String info, Date date) {
    this.project = project;
    this.branch = branch;
    this.destination = destination;
    this.ref = ref;
    this.info = info;
    this.lastUpdated = date;
  }

  public String getProject() {
    return project;
  }

  public String getBranch() {
    return branch;
  }

  public String getDestination() {
    return destination;
  }

  public String getRef() {
    return ref;
  }

  public void setRef(String ref) {
    this.ref = ref;
  }

  public String getInfo() {
    return info;
  }

  public void setInfo(String info) {
    this.info = info;
  }

  public Date getLastUpdated() {
    return lastUpdated;
  }

  public void save() {
    lastUpdated = new Date();
    log.debug(String.format("Saving Usage: %s, %s, %s, %s", project, branch,
        destination, ref));
    table.insertOrUpdate(this);
  }

  public void delete() {
    log.debug(String.format("Deleting Usage: %s, %s, %s", project, branch,
        destination));
    table.delete(this);
  }

  public static List<Usage> fetchByProject(String project) {
    return table.fetchByProject(project);
  }

  public static List<Usage> fetchByProject(String project, String branch) {
    return table.fetchByProject(project, branch);
  }

  public static List<Usage> fetchByDependency(String dependency) {
    return table.fetchByDependency(dependency);
  }

  public static void deleteByBranch(String project, String branch) {
    log.debug(String.format("Deleting all uses: %s, %s", project, branch));
    table.deleteByBranch(project, branch);
  }

  static class Table {
    private static final String TABLE_NAME = "RepoUsage";
    private static final String PROJECT = "project";
    private static final String BRANCH = "branch";
    private static final String DESTINATION = "destination";
    private static final String REF = "ref";
    private static final String INFO = "info";
    private static final String DATE = "last_update";
    @Inject
    private static SQLDriver sql;

    public Table() {
      // Create the table if it doesn't exist
      createTable();
    }

    private void createTable() {
      StringBuilder query = new StringBuilder();
      query.append(String.format("CREATE TABLE IF NOT EXISTS %s(", TABLE_NAME));
      query.append(String.format("%s VARCHAR(1023),", PROJECT));
      query.append(String.format("%s VARCHAR(255),", BRANCH));
      query.append(String.format("%s VARCHAR(1023),", DESTINATION));
      query.append(String.format("%s VARCHAR(255),", REF));
      query.append(String.format("%s VARCHAR(255),", INFO));
      query.append(String.format("%s TIMESTAMP DEFAULT NOW(),", DATE));
      query.append(String.format("PRIMARY KEY (%s, %s, %s))", PROJECT, BRANCH,
          DESTINATION));
      try {
        sql.execute(query.toString());
      } catch (SQLException e) {
        log.error("Unable to create Usage table", e);
      }
    }

    public void insertOrUpdate(Usage u) {
      if (fetchByProject(u.getProject(), u.getBranch(), u.getDestination())
          .isEmpty()) {
        String query = "INSERT INTO " + TABLE_NAME + "(" + PROJECT + ", "
            + BRANCH + ", " + DESTINATION + ", " + REF + ", " + INFO + ", "
            + DATE + ") VALUES (?, ?, ?, ?, ?, " + sql.getDateFormat() + ")";
        try {
          sql.execute(query, u.getProject(), u.getBranch(), u.getDestination(),
              u.getRef(), u.getInfo(), sql.getDateAsString(u.getLastUpdated()));
        } catch (SQLException e) {
          log.error("Unable to insert usage", e);
        }
      } else {
        String query = "UPDATE " + TABLE_NAME + " SET " + REF + "=?, " + INFO
            + "=?, " + DATE + "=" + sql.getDateFormat() + " WHERE " + PROJECT
            + "=? AND " + BRANCH + "=? AND " + DESTINATION + "=?";
        try {
          sql.execute(query, u.getRef(), u.getInfo(),
              sql.getDateAsString(u.getLastUpdated()), u.getProject(),
              u.getBranch(), u.getDestination());
        } catch (SQLException e) {
          log.error("Unable to update usage", e);
        }
      }
    }

    public void delete(Usage u) {
      String query = "DELETE FROM " + TABLE_NAME + " WHERE " + PROJECT
          + "=? AND " + BRANCH + "=? AND " + DESTINATION + "=?";
      try {
        sql.execute(query, u.getProject(), u.getBranch(), u.getDestination());
      } catch (SQLException e) {
        log.error("Unable to delete usage", e);
      }
    }

    public void deleteByBranch(String project, String branch) {
      String query = "DELETE FROM " + TABLE_NAME + " WHERE " + PROJECT
          + "=? AND " + BRANCH + "=?";
      try {
        sql.execute(query, project, branch);
      } catch (SQLException e) {
        log.error("Unable to delete usage", e);
      }
    }

    public List<Usage> fetchByProject(String project) {
      String query = "SELECT " + PROJECT + ", " + BRANCH + ", " + DESTINATION
          + ", " + REF + ", " + INFO + ", " + DATE + " FROM " + TABLE_NAME
          + " WHERE " + PROJECT + "=?";
      try {
        return loadUsage(sql.fetchRows(query, project));

      } catch (SQLException e) {
        log.error("Unable to execute query", e);
      }
      return Collections.emptyList();
    }

    public List<Usage> fetchByProject(String project, String branch) {
      String query = "SELECT " + PROJECT + ", " + BRANCH + ", " + DESTINATION
          + ", " + REF + ", " + INFO + ", " + DATE + " FROM " + TABLE_NAME
          + " WHERE " + PROJECT + "=? AND " + BRANCH + "=?";
      try {
        return loadUsage(sql.fetchRows(query, project, branch));

      } catch (SQLException e) {
        log.error("Unable to execute query", e);
      }
      return Collections.emptyList();
    }

    public List<Usage> fetchByProject(String project, String branch,
        String destination) {
      String query =
          "SELECT " + PROJECT + ", " + BRANCH + ", " + DESTINATION + ", " + REF
              + ", " + INFO + ", " + DATE + " FROM " + TABLE_NAME + " WHERE "
              + PROJECT + "=? AND " + BRANCH + "=? AND " + DESTINATION + "=?";
      try {
        return loadUsage(sql.fetchRows(query, project, branch, destination));

      } catch (SQLException e) {
        log.error("Unable to execute query", e);
      }
      return Collections.emptyList();
    }

    public List<Usage> fetchByDependency(String dependency) {
      String query = "SELECT " + PROJECT + ", " + BRANCH + ", " + DESTINATION
          + ", " + REF + ", " + INFO + ", " + DATE + " FROM " + TABLE_NAME
          + " WHERE " + DESTINATION + "=?";
      try {
        return loadUsage(sql.fetchRows(query, dependency));

      } catch (SQLException e) {
        log.error("Unable to execute query", e);
      }
      return Collections.emptyList();
    }

    private List<Usage> loadUsage(List<Map<String, String>> rows) {
      List<Usage> result = new ArrayList<>();
      for (Map<String, String> row : rows) {
        Usage tmp = new Usage(row.get(PROJECT), row.get(BRANCH),
            row.get(DESTINATION), row.get(REF), row.get(INFO),
            sql.getStringAsDate(row.get(DATE)));
        result.add(tmp);
      }
      return result;
    }
  }
}
