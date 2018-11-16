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
package com.google.gerrit.server.util;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefUpdater {
  private static final Logger log = LoggerFactory.getLogger(RefUpdater.class);

  public class Args {
    public final Branch.NameKey branch;
    public ObjectId expectedOldObjectId;
    public ObjectId newObjectId;
    public boolean isForceUpdate;
    public PersonIdent refLogIdent;
    public String refLogMessage;

    public Args(Branch.NameKey branch) {
      this.branch = branch;
      CurrentUser user = userProvider.get();
      if (user instanceof IdentifiedUser) {
        refLogIdent = ((IdentifiedUser) user).newRefLogIdent();
      } else {
        refLogIdent = gerrit;
      }
    }
  }

  protected final Provider<CurrentUser> userProvider;
  protected final @GerritPersonIdent PersonIdent gerrit;
  protected final GitRepositoryManager repoManager;
  protected final GitReferenceUpdated gitRefUpdated;
  protected final TagCache tagCache;
  protected final AccountCache accountCache;

  @Inject
  RefUpdater(
      AccountCache accountCache,
      Provider<CurrentUser> userProvider,
      @GerritPersonIdent PersonIdent gerrit,
      GitRepositoryManager repoManager,
      TagCache tagCache,
      GitReferenceUpdated gitRefUpdated) {
    this.accountCache = accountCache;
    this.userProvider = userProvider;
    this.gerrit = gerrit;
    this.repoManager = repoManager;
    this.tagCache = tagCache;
    this.gitRefUpdated = gitRefUpdated;
  }

  public void update(Branch.NameKey branch, ObjectId oldRefId, ObjectId newRefId)
      throws IOException, NoSuchProjectException {
    this.update(branch, oldRefId, newRefId, null);
  }

  public void update(
      Branch.NameKey branch, ObjectId oldRefId, ObjectId newRefId, String refLogMessage)
      throws IOException, NoSuchProjectException {
    Args args = new Args(branch);
    args.expectedOldObjectId = oldRefId;
    args.newObjectId = newRefId;
    args.refLogMessage = refLogMessage;
    this.update(args);
  }

  public void forceUpdate(Branch.NameKey branch, ObjectId newRefId)
      throws IOException, NoSuchProjectException {
    this.forceUpdate(branch, newRefId, null);
  }

  public void forceUpdate(Branch.NameKey branch, ObjectId newRefId, String refLogMessage)
      throws IOException, NoSuchProjectException {
    Args args = new Args(branch);
    args.newObjectId = newRefId;
    args.isForceUpdate = true;
    args.refLogMessage = refLogMessage;
    update(args);
  }

  public void delete(Branch.NameKey branch) throws IOException, NoSuchProjectException {
    Args args = new Args(branch);
    args.newObjectId = ObjectId.zeroId();
    args.isForceUpdate = true;
    update(args);
  }

  public void update(Args args) throws IOException, NoSuchProjectException {
    new Update(args).update();
  }

  protected class Update {
    protected Repository repo;
    protected Args args;
    protected RefUpdate update;
    protected Branch.NameKey branch;
    protected Project.NameKey project;
    protected boolean delete;

    protected Update(Args args) {
      this.args = args;
      branch = args.branch;
      project = branch.getParentKey();
      delete = args.newObjectId.equals(ObjectId.zeroId());
    }

    protected void update() throws IOException, NoSuchProjectException {
      try {
        repo = repoManager.openRepository(project);
        try {
          initUpdate();
          handleResult(runUpdate());
        } catch (IOException err) {
          log.error("RefUpdate failed: branch not updated: " + branch.get(), err);
          throw err;
        } finally {
          repo.close();
          repo = null;
        }
      } catch (RepositoryNotFoundException e) {
        throw new NoSuchProjectException(project);
      }
    }

    protected void initUpdate() throws IOException {
      update = repo.updateRef(branch.get());
      update.setExpectedOldObjectId(args.expectedOldObjectId);
      update.setNewObjectId(args.newObjectId);
      update.setRefLogIdent(args.refLogIdent);
      update.setForceUpdate(args.isForceUpdate);
      if (args.refLogMessage != null) {
        update.setRefLogMessage(args.refLogMessage, true);
      }
    }

    protected RefUpdate.Result runUpdate() throws IOException {
      if (delete) {
        return update.delete();
      }
      return update.update();
    }

    protected void handleResult(RefUpdate.Result result) throws IOException {
      switch (result) {
        case FORCED:
          if (!delete && !args.isForceUpdate) {
            throw new IOException(result.name());
          }
          // $FALL-THROUGH$
        case FAST_FORWARD:
        case NEW:
        case NO_CHANGE:
          onUpdated(update, args);
          break;
        case IO_FAILURE:
        case LOCK_FAILURE:
        case NOT_ATTEMPTED:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
        case REJECTED_MISSING_OBJECT:
        case REJECTED_OTHER_REASON:
        case RENAMED:
        default:
          throw new IOException(result.name());
      }
    }

    protected void onUpdated(RefUpdate update, Args args) {
      if (update.getResult() == RefUpdate.Result.FAST_FORWARD) {
        tagCache.updateFastForward(
            project, update.getName(), update.getOldObjectId(), args.newObjectId);
      }
      if (userProvider.get().isIdentifiedUser()) {
        AccountState accountState = accountCache.get(userProvider.get().getAccountId()).get();
        gitRefUpdated.fire(project, update, accountState);
      }
    }
  }
}
