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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.Batch;
import com.googlesource.gerrit.plugins.batch.BatchStore;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/** Parses a query string meant to be applied to batch objects. */
public class BatchQueryBuilder extends QueryBuilder<Batch, BatchQueryBuilder> {
  public abstract static class SimplePredicate extends OperatorPredicate<Batch>
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

  protected static final QueryBuilder.Definition<Batch, BatchQueryBuilder> mydef =
      new QueryBuilder.Definition<>(BatchQueryBuilder.class);

  public static final long DEFAULT_SECONDS = TimeUnit.SECONDS.convert(3, TimeUnit.DAYS);

  protected final ProjectCache projectCache;
  protected final PluginConfigFactory cfgFactory;
  protected final BatchStore store;
  protected final String pluginName;

  @Inject
  public BatchQueryBuilder(
      ProjectCache projectCache,
      PluginConfigFactory cfgFactory,
      BatchStore store,
      @PluginName String pluginName) {
    super(mydef, null);
    this.projectCache = projectCache;
    this.cfgFactory = cfgFactory;
    this.store = store;
    this.pluginName = pluginName;
  }

  public Date getExpiry() {
    Config config = cfgFactory.getProjectPluginConfig(projectCache.getAllProjects(), pluginName);
    long seconds =
        ConfigUtil.getTimeUnit(
            config, "cleaner", null, "maxAge", DEFAULT_SECONDS, TimeUnit.SECONDS);
    long ms = TimeUnit.MILLISECONDS.convert(seconds, TimeUnit.SECONDS);
    return new Date(new Date().getTime() - ms);
  }

  @Operator
  public Predicate<Batch> is(String value) throws QueryParseException {
    if ("expired".equalsIgnoreCase(value)) {
      return new SimplePredicate("is", value) {
        Date expiry = getExpiry();

        @Override
        public boolean match(Batch b) {
          return b.lastModified.before(expiry);
        }
      };
    }
    throw error("Invalid query");
  }
}
