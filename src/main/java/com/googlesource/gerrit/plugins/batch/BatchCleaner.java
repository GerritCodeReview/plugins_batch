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
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Clean up expired batches daily. */
public class BatchCleaner implements CancelableRunnable {
  private static final Logger log = LoggerFactory.getLogger(BatchCleaner.class);

  public static class Lifecycle implements LifecycleListener {
    public static final long DEFAULT_START_MINUTES = MINUTES.convert(1, MINUTES);
    public static final long DEFAULT_MINUTES = MINUTES.convert(1, DAYS);

    protected final long startMinutes;
    protected final long intervalMinutes;

    protected final PluginConfigFactory pluginConfigFactory;
    protected final WorkQueue workQueue;
    protected final BatchCleaner cleaner;

    @Inject
    protected Lifecycle(
        PluginConfigFactory pluginConfigFactory, WorkQueue workQueue, BatchCleaner cleaner)
        throws NoSuchProjectException {
      this.pluginConfigFactory = pluginConfigFactory;
      this.workQueue = workQueue;
      this.cleaner = cleaner;
      startMinutes = startDelay();
      intervalMinutes = interval();
    }

    @Override
    public void start() {
      cleaner.future =
          workQueue
              .getDefaultQueue()
              .scheduleWithFixedDelay(cleaner, startMinutes, intervalMinutes, MINUTES);
    }

    @Override
    public void stop() {
      cleaner.cancel();
    }

    protected long startDelay() throws NoSuchProjectException {
      Config config = pluginConfigFactory.getConfig();
      return ConfigUtil.getTimeUnit(
          config, "cleaner", null, "startDelay", DEFAULT_START_MINUTES, MINUTES);
    }

    protected long interval() throws NoSuchProjectException {
      Config config = pluginConfigFactory.getConfig();
      String freq = config.getString("cleaner", null, "interval");
      if (freq != null && ("disabled".equalsIgnoreCase(freq) || "off".equalsIgnoreCase(freq))) {
        return Long.MAX_VALUE;
      }
      return ConfigUtil.getTimeUnit(config, "cleaner", null, "interval", DEFAULT_MINUTES, MINUTES);
    }
  }

  protected final ListBatches list;
  protected final BatchRemover remover;
  protected final Provider<CurrentUser> userProvider;

  protected ScheduledFuture<?> future;
  protected volatile boolean canceled = false;

  @Inject
  protected BatchCleaner(
      ListBatches list, BatchRemover remover, Provider<CurrentUser> userProvider) {
    this.remover = remover;
    this.list = list;

    this.userProvider = userProvider;
    list.query = new ArrayList<String>();
    list.query.add("is:expired");
  }

  @Override
  public void run() {
    Iterable<Batch> batches = null;
    try {
      batches = list.getBatches();
    } catch (Exception e) {
      log.error("getting list of batches to clean.", e);
      // Ignore errors and hope someone notices the log file and fixes before the next run
      return;
    }

    for (Batch batch : batches) {
      if (canceled) {
        return;
      }
      try {
        remover.remove(batch.id);
      } catch (Exception e) {
        log.error("cleaning batch: " + batch.id, e);
        // Ignore errors and hope someone notices the log file and fixes before the next run
      }
    }
  }

  @Override
  public void cancel() {
    if (future != null) {
      future.cancel(true);
      canceled = true;
    }
  }

  @Override
  public String toString() {
    return "Batch Cleaner";
  }
}
