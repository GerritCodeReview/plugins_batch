// Copyright (C) 2016 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.batch.query;

import com.googlesource.gerrit.plugins.batch.Batch;
import com.googlesource.gerrit.plugins.batch.BatchStore;

import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.Matchable;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Parses a query string meant to be applied to batch objects.
 */
public class BatchQueryBuilder extends QueryBuilder<Batch> {
  public static abstract class SimplePredicate extends
      OperatorPredicate<Batch>
      implements Matchable<Batch> {
    public SimplePredicate(String op, String val) {
      super(op, val);
    }

    @Override
    public boolean match(Batch b) {
      return false;
    }

    @Override
    public int getCost() {
      return 1;
    }
  }

  private static final QueryBuilder.Definition<Batch, BatchQueryBuilder>
      mydef = new QueryBuilder.Definition<Batch, BatchQueryBuilder>(
          BatchQueryBuilder.class);

  private static final long DEFAULT_SECONDS =
      TimeUnit.SECONDS.convert(3, TimeUnit.DAYS);

  private ProjectCache projectCache;
  private PluginConfigFactory pluginConfigFactory;
  private BatchStore store;

  @Inject
  public BatchQueryBuilder(ProjectCache projectCache,
    PluginConfigFactory pluginConfigFactory, BatchStore store) {
    super(mydef);
    this.projectCache = projectCache;
    this.pluginConfigFactory = pluginConfigFactory;
    this.store = store;
  }

  public Date getExpiry() throws NoSuchProjectException {
    Config cfg = pluginConfigFactory.getProjectPluginConfig(
        projectCache.getAllProjects(), "batch");
    long seconds = ConfigUtil.getTimeUnit(cfg, "cleaner", null, "maxAge",
        DEFAULT_SECONDS, TimeUnit.SECONDS);
    long ms = TimeUnit.MILLISECONDS.convert(seconds, TimeUnit.SECONDS);
    return new Date(new Date().getTime() - ms);
  }

  @Operator
  public Predicate<Batch> expired(final String val)
      throws NoSuchProjectException {
    return new SimplePredicate("expired", val) {
          Date expiry = getExpiry();
          @Override
          public boolean match(Batch b) {
            return b.lastModified.before(expiry);
          }
        };
  }
}
