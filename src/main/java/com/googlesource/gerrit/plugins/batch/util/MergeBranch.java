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

package com.googlesource.gerrit.plugins.batch.util;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.batch.exception.InvalidRevisionException;
import com.googlesource.gerrit.plugins.batch.exception.MergeException;
import com.googlesource.gerrit.plugins.batch.util.MergeBuilder.FastForwardMode;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;

public class MergeBranch implements Callable<ObjectId> {
  public interface Factory {
    MergeBranch create(
        @Assisted Branch.NameKey destBranch,
        @Assisted("destSha") @Nullable String destSha,
        @Assisted("sourceRef") String srcName,
        @Assisted MergeStrategy strategy,
        @Assisted FastForwardMode fastForwardMode,
        @Assisted("message") String message);
  }

  private ProjectCache projectCache;
  private GitRepositoryManager repoManager;
  private MergeBuilder.Factory builderFactory;

  private Project.NameKey projectName;
  private String destName;
  private ObjectId destId;
  private String srcName;
  private String message;
  private MergeStrategy strategy;
  private FastForwardMode fastForwardMode = FastForwardMode.FF;

  @Inject
  MergeBranch(
      GitRepositoryManager repoManager,
      ProjectCache pc,
      MergeBuilder.Factory builderFactory,
      @Assisted Branch.NameKey destBranch,
      @Assisted("destSha") @Nullable String destSha,
      @Assisted("sourceRef") String srcName,
      @Assisted("message") @Nullable String message,
      @Assisted @Nullable MergeStrategy strategy,
      @Assisted @Nullable FastForwardMode fastForwardMode) {
    this.projectCache = pc;
    this.repoManager = repoManager;
    this.builderFactory = builderFactory;

    this.projectName = destBranch.getParentKey();
    destName = RefNames.fullName(destBranch.get());
    if (destSha != null) {
      this.destId = ObjectId.fromString(destSha);
    }
    this.srcName = srcName;
    this.message = message;
    this.strategy = strategy;
    if (fastForwardMode != null) {
      this.fastForwardMode = fastForwardMode;
    }
  }

  @Override
  public ObjectId call()
      throws IOException, NoSuchRefException, RepositoryNotFoundException, MergeException,
          InvalidRevisionException {
    Repository repo = repoManager.openRepository(projectName);
    try {
      Ref destRef = repo.getRefDatabase().getRef(destName);
      if (destRef == null) {
        throw new NoSuchRefException(destName);
      }
      if (destId == null) {
        destId = repo.resolve(destName);
        if (destId == null) {
          throw new InvalidRevisionException();
        }
      }
      ObjectId srcId = repo.resolve(srcName);
      if (srcId == null) {
        throw new InvalidRevisionException();
      }
      strategy = defaultStrategy(strategy);
      return builderFactory
          .create(projectName, message, strategy, fastForwardMode, destId, srcId)
          .call();
    } finally {
      repo.close();
    }
  }

  private MergeStrategy defaultStrategy(MergeStrategy strategy) {
    if (strategy == null) {
      if (projectCache.get(projectName).is(BooleanProjectConfig.USE_CONTENT_MERGE)) {
        return MergeStrategy.RESOLVE;
      } else {
        return MergeStrategy.SIMPLE_TWO_WAY_IN_CORE;
      }
    }
    return strategy;
  }
}
