// Copyright (C) 2018 The Android Open Source Project
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

package com.googlesource.gerrit.plugins.batch;

import com.google.common.base.Joiner;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.query.BatchQueryBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
public class ListBatches implements RestReadView<TopLevelResource> {
  @Argument(
    index = 0,
    required = false,
    multiValued = true,
    metaVar = "QUERY",
    usage = "Query to execute"
  )
  public List<String> query;

  @Option(
    name = "--include-last-modified",
    usage = "include the time of the last modification for every batch"
  )
  private boolean includeLastModified;

  private BatchQueryBuilder queryBuilder;
  private BatchStore store;

  @Inject
  ListBatches(BatchQueryBuilder queryBuilder, BatchStore store) {
    this.queryBuilder = queryBuilder;
    this.store = store;
  }

  @Override
  public Object apply(TopLevelResource resource) throws Exception {
    return OutputFormat.JSON
        .newGson()
        .toJsonTree(getBatches(), new TypeToken<List<Object>>() {}.getType());
  }

  public void display(OutputStream displayOutputStream)
      throws IOException, OrmException, QueryParseException {
    try {
      PrintWriter stdout =
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(displayOutputStream, "UTF-8")));
      try {
        OutputFormat.JSON
            .newGson()
            .toJson(getBatches(), new TypeToken<List<Batch>>() {}.getType(), stdout);
        stdout.print('\n');
      } finally {
        stdout.flush();
      }
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("JVM lacks UTF-8 encoding", e);
    }
  }

  public List<Batch> getBatches() throws IOException, OrmException, QueryParseException {
    Predicate<Batch> pred = null;
    if (query != null) {
      pred = queryBuilder.parse(Joiner.on(" ").join(query));
      includeLastModified = true;
    }

    List<Batch> batches = new ArrayList<Batch>();
    for (Batch batch : store.find(includeLastModified)) {
      boolean matched = pred == null;
      if (!matched && pred.isMatchable() && pred.asMatchable().match(batch)) {
        matched = true;
      }
      if (matched) {
        batches.add(batch);
      }
    }
    return batches;
  }
}
