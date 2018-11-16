// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.submit.IntegrationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.StringUtils;

public class MergeBuilder implements Callable<ObjectId> {
  /**
   * The modes available for fast forward merges corresponding to the --ff, --no-ff and --ff-only
   * options
   */
  public static enum FastForwardMode {
    /**
     * Corresponds to the default --ff option (for a fast forward update the branch pointer only).
     */
    FF("ff"),
    /** Corresponds to the --no-ff option (create a merge commit even for a fast forward). */
    NO_FF("no-ff"),
    /**
     * Corresponds to the --ff-only option (abort unless the merge is a fast forward or branch is
     * already up-to-date).
     */
    FF_ONLY("ff-only");
    public final String name;

    private FastForwardMode(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public static FastForwardMode fromString(String str) {
      if (StringUtils.isEmptyOrNull(str)) {
        return null;
      }
      for (FastForwardMode mode : FastForwardMode.values()) {
        if (mode.getName().equalsIgnoreCase(str)) {
          return mode;
        }
      }
      return null;
    }
  }

  public interface Factory {
    MergeBuilder create(
        @Assisted Project.NameKey project,
        @Assisted("message") @Nullable String message,
        @Assisted @Nullable MergeStrategy strategy,
        @Assisted @Nullable FastForwardMode fastForwardMode,
        @Assisted("firstParent") ObjectId firstParent,
        @Assisted("secondParent") ObjectId secondParent);
  }

  protected final GitRepositoryManager repoManager;
  protected final PersonIdent gerrit;
  protected final IdentifiedUser user;
  protected final Project.NameKey project;
  protected String message;
  protected final MergeStrategy strategy;
  protected FastForwardMode fastForwardMode = FastForwardMode.FF;
  protected final ObjectId firstParent;
  protected final ObjectId secondParent;

  @Inject
  MergeBuilder(
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent gerrit,
      IdentifiedUser user,
      @Assisted Project.NameKey project,
      @Assisted("message") @Nullable String message,
      @Assisted @Nullable MergeStrategy strategy,
      @Assisted @Nullable FastForwardMode fastForwardMode,
      @Assisted("firstParent") ObjectId firstParent,
      @Assisted("secondParent") ObjectId secondParent) {
    this.repoManager = repoManager;
    this.gerrit = gerrit;
    this.user = user;
    this.project = project;
    this.message = message;
    this.strategy = strategy;
    if (fastForwardMode != null) {
      this.fastForwardMode = fastForwardMode;
    }
    this.firstParent = firstParent;
    this.secondParent = secondParent;
  }

  @Override
  public ObjectId call() throws IOException, IntegrationException {
    try (Repository repo = repoManager.openRepository(project)) {
      return build(repo);
    }
  }

  public ObjectId build(Repository repo) throws IOException, IntegrationException {
    try (RevWalk revWalk = new RevWalk(repo)) {
      RevCommit firstParentCommit = revWalk.lookupCommit(firstParent);
      RevCommit secondParentCommit = revWalk.lookupCommit(secondParent);
      if (revWalk.isMergedInto(secondParentCommit, firstParentCommit)) {
        return firstParent; // already up to date
      }
      if (fastForwardMode != FastForwardMode.NO_FF
          && revWalk.isMergedInto(firstParentCommit, secondParentCommit)) {
        return secondParent; // Fast forward merge
      }
      if (fastForwardMode == FastForwardMode.FF_ONLY) {
        throw new IntegrationException("Merge aborted"); // because not FF
      }
      return merge(repo, revWalk);
    }
  }

  protected ObjectId merge(Repository repo, RevWalk revWalk)
      throws IOException, IntegrationException {
    ThreeWayMerger merger = getMerger(repo);
    if (!merger.merge(firstParent, secondParent)) {
      throw new IntegrationException("Merge conflict");
    }
    message = defaultMessage(revWalk, message);
    return insert(merger, buildCommit(merger));
  }

  protected ThreeWayMerger getMerger(Repository repo) {
    if (strategy == MergeStrategy.RESOLVE) {
      return MergeStrategy.RESOLVE.newMerger(repo, true);
    }
    return MergeStrategy.SIMPLE_TWO_WAY_IN_CORE.newMerger(repo);
  }

  protected CommitBuilder buildCommit(Merger merger) {
    final CommitBuilder mergeCommit = new CommitBuilder();
    mergeCommit.setTreeId(merger.getResultTreeId());
    mergeCommit.setParentIds(firstParent, secondParent);
    mergeCommit.setAuthor(
        user.newCommitterIdent(new Timestamp(System.currentTimeMillis()), gerrit.getTimeZone()));
    mergeCommit.setCommitter(gerrit);
    mergeCommit.setMessage(message);
    return mergeCommit;
  }

  protected String defaultMessage(RevWalk walk, String message) {
    if (message == null) {
      try {
        message = walk.parseCommit(secondParent).getShortMessage();
      } catch (Exception e) {
        message = secondParent.getName();
      }
      message = "Merge \"" + message + "\"";
    }
    return message;
  }

  protected ObjectId insert(Merger merger, CommitBuilder commit) throws IOException {
    try (ObjectInserter objInserter = merger.getObjectInserter()) {
      ObjectId mergeCommitId = objInserter.insert(commit);
      objInserter.flush();
      return mergeCommitId;
    }
  }
}
