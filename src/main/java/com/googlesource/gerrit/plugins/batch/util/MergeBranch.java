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
package com.googlesource.gerrit.plugins.batch.util;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.googlesource.gerrit.plugins.batch.util.MergeBuilder.FastForwardMode;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;

public class MergeBranch implements Callable<ObjectId> {
  public interface Factory {
    MergeBranch create(
        @Assisted BranchNameKey destBranch,
        @Assisted("destSha") @Nullable String destSha,
        @Assisted("sourceRef") String srcName,
        @Assisted MergeStrategy strategy,
        @Assisted FastForwardMode fastForwardMode,
        @Assisted("message") String message);
  }

  protected final GitRepositoryManager repoManager;
  protected final ProjectCache projectCache;
  protected final MergeBuilder.Factory builderFactory;
  protected final Project.NameKey projectName;
  protected final String destName;
  protected ObjectId destId;
  protected final String srcName;
  protected final String message;
  protected MergeStrategy strategy;
  protected FastForwardMode fastForwardMode = FastForwardMode.FF;

  @Inject
  MergeBranch(
      GitRepositoryManager repoManager,
      ProjectCache projectCache,
      MergeBuilder.Factory builderFactory,
      @Assisted BranchNameKey destBranch,
      @Assisted("destSha") @Nullable String destSha,
      @Assisted("sourceRef") String srcName,
      @Assisted("message") @Nullable String message,
      @Assisted @Nullable MergeStrategy strategy,
      @Assisted @Nullable FastForwardMode fastForwardMode) {
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.builderFactory = builderFactory;
    this.projectName = destBranch.project();
    destName = RefNames.fullName(destBranch.branch());
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
      throws IOException, NoSuchRefException, RepositoryNotFoundException, BadRequestException,
          MergeConflictException {
    try (Repository repo = repoManager.openRepository(projectName)) {
      Ref destRef = repo.getRefDatabase().exactRef(destName);
      if (destRef == null) {
        throw new NoSuchRefException(destName);
      }
      if (destId == null) {
        destId = repo.resolve(destName);
        if (destId == null) {
          throw new BadRequestException("Invalid Revision");
        }
      }
      ObjectId srcId = repo.resolve(srcName);
      if (srcId == null) {
        throw new BadRequestException("Invalid Revision");
      }
      strategy = defaultStrategy(strategy);
      return builderFactory
          .create(projectName, message, strategy, fastForwardMode, destId, srcId)
          .call();
    }
  }

  protected MergeStrategy defaultStrategy(MergeStrategy strategy) {
    if (strategy == null) {
      Optional<ProjectState> project = projectCache.get(projectName);
      if (project.isPresent() && project.get().is(BooleanProjectConfig.USE_CONTENT_MERGE)) {
        return MergeStrategy.RESOLVE;
      }
      return MergeStrategy.SIMPLE_TWO_WAY_IN_CORE;
    }
    return strategy;
  }
}
