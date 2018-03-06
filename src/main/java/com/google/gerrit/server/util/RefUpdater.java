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

// TODO - this file stays

package com.google.gerrit.server.util;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.TagCache;
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

  private Provider<CurrentUser> userProvider;
  private @GerritPersonIdent PersonIdent gerrit;
  private GitRepositoryManager repoManager;
  private GitReferenceUpdated gitRefUpdated;
  private IdentifiedUser user;
  private TagCache tagCache;

  @Inject
  RefUpdater(
      Provider<CurrentUser> userProvider,
      @GerritPersonIdent PersonIdent gerrit,
      GitRepositoryManager repoManager,
      TagCache tagCache,
      GitReferenceUpdated gitRefUpdated) {
    this.userProvider = userProvider;
    this.gerrit = gerrit;
    this.repoManager = repoManager;
    this.tagCache = tagCache;
    this.gitRefUpdated = gitRefUpdated;
  }

  public void update(Branch.NameKey branch, ObjectId oldRefId, ObjectId newRefId)
      throws IOException, RepositoryNotFoundException {
    Args args = new Args(branch);
    args.expectedOldObjectId = oldRefId;
    args.newObjectId = newRefId;
    update(args);
  }

  public void forceUpdate(Branch.NameKey branch, ObjectId newRefId)
      throws IOException, RepositoryNotFoundException {
    Args args = new Args(branch);
    args.newObjectId = newRefId;
    args.isForceUpdate = true;
    update(args);
  }

  public void delete(Branch.NameKey branch) throws IOException, RepositoryNotFoundException {
    Args args = new Args(branch);
    args.newObjectId = ObjectId.zeroId();
    args.isForceUpdate = true;
    update(args);
  }

  public void update(Args args) throws IOException {
    new Update(args).update();
  }

  private class Update {
    Repository repo;
    Args args;
    RefUpdate update;
    Branch.NameKey branch;
    Project.NameKey project;
    boolean delete;

    Update(Args args) throws IOException {
      this.args = args;
      branch = args.branch;
      project = branch.getParentKey();
      delete = args.newObjectId.equals(ObjectId.zeroId());
    }

    void update() throws IOException {
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
    }

    void initUpdate() throws IOException {
      update = repo.updateRef(branch.get());
      update.setExpectedOldObjectId(args.expectedOldObjectId);
      update.setNewObjectId(args.newObjectId);
      update.setRefLogIdent(args.refLogIdent);
      update.setForceUpdate(args.isForceUpdate);
    }

    RefUpdate.Result runUpdate() throws IOException {
      if (delete) {
        return update.delete();
      }
      return update.update();
    }

    void handleResult(RefUpdate.Result result) throws IOException {
      switch (result) {
        case FORCED:
          if (!delete && !args.isForceUpdate) {
            throw new IOException(result.name());
          }
        case FAST_FORWARD:
        case NEW:
        case NO_CHANGE:
          onUpdated(update, args);
          break;
        default:
          throw new IOException(result.name());
      }
    }

    void onUpdated(RefUpdate update, Args args) {
      if (update.getResult() == RefUpdate.Result.FAST_FORWARD) {
        tagCache.updateFastForward(
            project, update.getName(), update.getOldObjectId(), args.newObjectId);
      }
      CurrentUser user = userProvider.get();
      if (user instanceof IdentifiedUser) {
        Account account = ((IdentifiedUser) user).getAccount();
        AccountState test =
            AccountState.forAccount(new AllUsersName(AllUsersNameProvider.DEFAULT), account);
        gitRefUpdated.fire(project, update, test);
      }
    }
  }
}
