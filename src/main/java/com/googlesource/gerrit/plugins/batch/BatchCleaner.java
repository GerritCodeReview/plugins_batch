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

package com.googlesource.gerrit.plugins.batch;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;


import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;

/** Clean up expired batches daily. */
public class BatchCleaner implements CancelableRunnable {
  private static final Logger log = LoggerFactory.getLogger(BatchCleaner.class);

  public static class Lifecycle implements LifecycleListener {
    static final long DEFAULT_START_MINUTES = MINUTES.convert(1, MINUTES);
    static final long DEFAULT_MINUTES = MINUTES.convert(1, DAYS);

    private ProjectCache projectCache;
    private PluginConfigFactory pluginConfigFactory;
    private WorkQueue.Executor threadPool;
    private BatchCleaner cleaner;

    private long startMinutes;
    private long intervalMinutes;

    @Inject
    Lifecycle(ProjectCache projectCache,
        PluginConfigFactory pluginConfigFactory, WorkQueue workQueue,
        BatchCleaner cleaner) throws NoSuchProjectException {
      this.projectCache = projectCache;
      this.pluginConfigFactory = pluginConfigFactory;
      this.threadPool = workQueue.getDefaultQueue();
      this.cleaner = cleaner;
      startMinutes = startDelay();
      intervalMinutes = interval();
    }

    @Override
    public void start() {
      cleaner.future = threadPool.scheduleWithFixedDelay(cleaner,
          startMinutes, intervalMinutes, MINUTES);
    }

    @Override
    public void stop() {
      cleaner.cancel();
      threadPool.getQueue().remove(cleaner);
    }

    private long startDelay() throws NoSuchProjectException {
      Config cfg = pluginConfigFactory.getProjectPluginConfig(
        projectCache.getAllProjects(), "batch");
      return ConfigUtil.getTimeUnit(cfg, "cleaner", null, "startDelay",
          DEFAULT_START_MINUTES, MINUTES);
    }

    private long interval() throws NoSuchProjectException {
      Config cfg = pluginConfigFactory.getProjectPluginConfig(
        projectCache.getAllProjects(), "batch");
      String freq = cfg.getString("cleaner", null, "interval");
      if (freq != null
          && ("disabled".equalsIgnoreCase(freq) || "off".equalsIgnoreCase(freq))) {
        return Long.MAX_VALUE;
      }
      return ConfigUtil.getTimeUnit(cfg, "cleaner", null, "interval",
          DEFAULT_MINUTES, MINUTES);
    }
  }

  private ListBatches list;
  private BatchRemover remover;
  private Provider<CurrentUser> userProvider;

  private ScheduledFuture<?> future;
  private volatile boolean canceled = false;

  @Inject
  BatchCleaner(ListBatches list, BatchRemover remover,
      Provider<CurrentUser> userProvider) {
    this.remover = remover;
    this.list = list;

    this.userProvider = userProvider;
    list.query = new ArrayList<String>();
    list.query.add("expired:true");
  }

  @Override
  public void run() {
    Iterable<Batch> batches = null;
    try {
      batches = list.getBatches();
    } catch (Exception e) {
      log.error("getting list of batches to clean.", e);
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
    }

    for (Batch batch : batches) {
      if (canceled) {
        return;
      }
      try {
        remover.remove(batch.id);
      } catch (Exception e) {
        log.error("cleaning batch: " + batch.id, e);
        if (e instanceof RuntimeException) {
          throw (RuntimeException) e;
        }
      }
    }
  }

  @Override
  public void cancel() {
    canceled = true;
    future.cancel(true);
  }

  @Override
  public String toString() {
    return "Batch Cleaner";
  }
}
