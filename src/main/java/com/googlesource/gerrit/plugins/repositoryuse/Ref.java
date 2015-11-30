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

public class Ref {
  private static final Logger log =
      LoggerFactory.getLogger(Ref.class);
  private static Table table = new Table();

  private String project;
  private String ref;
  private String commit;
  private Date lastUpdated;

  public Ref(String project, String ref, String commit) {
    init(project, ref, commit, new Date());
  }

  public Ref(String project, String ref, String commit, Date date) {
    init(project, ref, commit, date);
  }

  private void init(String project, String ref, String commit, Date date) {
    this.project = project;
    this.ref = ref;
    this.commit = commit;
    this.lastUpdated = date;
  }

  public String getProject() {
    return project;
  }

  public String getRef() {
    return ref;
  }

  public String getCommit() {
    return commit;
  }

  public void setCommit(String commit) {
    this.commit = commit;
  }

  public Date getLastUpdated() {
    return lastUpdated;
  }

  public void save() {
    lastUpdated = new Date();
    table.insertOrUpdate(this);
    log.info(String.format("Saving Ref: %s, %s, %s", project, ref, commit));
  }

  public void delete() {
    log.info(String.format("Deleting Ref: %s, %s", project, ref));
    table.delete(this);
  }

  public static List<Ref> fetchByProject(String project) {
    return table.fetchByProject(project);
  }

  public static Ref fetchByRef(String project, String ref) {
    List<Ref> tmp = table.fetchByRef(project, ref);
    if (tmp.size() == 1) {
      return tmp.get(0);
    }
    return null;
  }

  /*static interface Table {
    public void insertOrUpdate(Ref u);
    public List<Ref> fetchByProject(String project);
    public List<Ref> fetchByRef(String project, String ref);
  }

  public interface TableFactory {
    Table create();
  }*/

  static class Table {
    private static final String TABLE_NAME = "RefStatus";
    private static final String PROJECT = "project";
    private static final String REF = "ref";
    private static final String COMMIT = "commit";
    private static final String DATE = "last_update";
    @Inject private static SQLDriver sql;

    public Table() {
      // Create the table if it doesn't exist
      createTable();
    }

    private void createTable() {
      StringBuilder query = new StringBuilder();
      query.append(String.format("CREATE TABLE IF NOT EXISTS %s(", TABLE_NAME));
      query.append(String.format("%s VARCHAR(1023),", PROJECT));
      query.append(String.format("%s VARCHAR(255),", REF));
      query.append(String.format("%s VARCHAR(40),", COMMIT));
      query.append(String.format("%s TIMESTAMP DEFAULT NOW(),", DATE));
      query.append(String.format("PRIMARY KEY (%s, %s))", PROJECT, REF));
      try {
        sql.execute(query.toString());
      } catch (SQLException e) {
        log.error("Unable to create Ref table", e);
      }
    }

    public void insertOrUpdate(Ref r) {
      if (fetchByRef(r.getProject(), r.getRef()).isEmpty()) {
        String query = "INSERT INTO " + TABLE_NAME + "(" + PROJECT + ", " + REF + ", " + COMMIT + ", " + DATE + ") VALUES (?, ?, ?, " + sql.getDateFormat() + ")";
        try {
          sql.execute(query, r.getProject(), r.getRef(), r.getCommit(), sql.getDateAsString(r.getLastUpdated()));
        } catch (SQLException e) {
          log.error("Unable to insert reference", e);
        }
      } else {
        String query = "UPDATE " + TABLE_NAME + " SET " + COMMIT + "=?, " + DATE + "=" + sql.getDateFormat() + " WHERE " + PROJECT + "=? AND " + REF + "=?";
        try {
          sql.execute(query, r.getCommit(), sql.getDateAsString(r.getLastUpdated()), r.getProject(), r.getRef());
        } catch (SQLException e) {
          log.error("Unable to update reference", e);
        }
      }
    }

    public void delete(Ref r) {
      String query = "DELETE FROM " + TABLE_NAME + " WHERE " + PROJECT + "=? AND " + REF + "=?";
      try {
        sql.execute(query, r.getProject(), r.getRef());
      } catch (SQLException e) {
        log.error("Unable to delete reference", e);
      }
    }


    public List<Ref> fetchByProject(String project) {
      String query = "SELECT " + PROJECT + ", " + REF + ", " + COMMIT + ", "
          + DATE + " FROM " + TABLE_NAME + " WHERE " + PROJECT + "=?";
      try {
        return loadRefs(sql.fetchRows(query, project));

      } catch (SQLException e) {
        log.error("Unable to execute query", e);
      }
      return Collections.emptyList();
    }

    public List<Ref> fetchByRef(String project, String ref) {
      String query = "SELECT " + PROJECT + ", " + REF + ", " + COMMIT + ", "
          + DATE + " FROM " + TABLE_NAME + " WHERE " + PROJECT + "=? AND " + REF + "=?";
      try {
        return loadRefs(sql.fetchRows(query, project, ref));
      } catch (SQLException e) {
        log.error("Unable to execute query", e);
      }
      return Collections.emptyList();
    }

    private List<Ref> loadRefs(List<Map<String, String>> rows) {
      List<Ref> result = new ArrayList<>();
      for (Map<String, String> row : rows) {
        Ref tmp =
            new Ref(row.get(PROJECT), row.get(REF), row.get(COMMIT), sql.getStringAsDate(row.get(DATE)));
        result.add(tmp);
      }
      return result;
    }
  }
}
