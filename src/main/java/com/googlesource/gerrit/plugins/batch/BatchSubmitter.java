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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergedByPushOp;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.RefUpdater;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchSubmitter {
  private static final Logger log = LoggerFactory.getLogger(BatchSubmitter.class);

  protected final ReviewDb db;
  protected final GitRepositoryManager repoManager;
  protected final RefUpdater refUpdater;
  protected final RequestScopePropagator requestScopePropagator;
  protected final BatchUpdate.Factory batchUpdateFactory;
  protected final MergedByPushOp.Factory mergedByPushOpFactory;
  protected final IdentifiedUser user;
  protected final ChangeNotes.Factory notesFactory;
  protected final PermissionBackend permissionBackend;
  protected final PatchSetUtil psUtil;
  protected final BatchStore store;
  protected final BatchRemover remover;

  @Inject
  BatchSubmitter(
      ReviewDb db,
      GitRepositoryManager repoManager,
      RefUpdater refUpdater,
      RequestScopePropagator requestScopePropagator,
      BatchUpdate.Factory batchUpdateFactory,
      MergedByPushOp.Factory mergedByPushOpFactory,
      IdentifiedUser user,
      ChangeNotes.Factory notesFactory,
      PermissionBackend permissionBackend,
      PatchSetUtil psUtil,
      BatchStore store,
      BatchRemover remover) {
    this.db = db;
    this.repoManager = repoManager;
    this.refUpdater = refUpdater;
    this.requestScopePropagator = requestScopePropagator;
    this.batchUpdateFactory = batchUpdateFactory;
    this.mergedByPushOpFactory = mergedByPushOpFactory;
    this.user = user;
    this.notesFactory = notesFactory;
    this.permissionBackend = permissionBackend;
    this.psUtil = psUtil;
    this.store = store;
    this.remover = remover;
  }

  public Batch submit(String id)
      throws IOException, IllegalStateException, NoSuchBatchException, NoSuchProjectException,
          OrmException, RestApiException, UpdateException, PermissionBackendException {
    Batch batch = store.read(id);
    if (batch.state == Batch.State.OPEN) {
      throw new IllegalStateException("Cannot submit batch " + id + " in state " + batch.state);
    }
    ensureCanSubmit(batch);
    submit(batch);
    remover.remove(batch);
    return batch;
  }

  private void ensureCanSubmit(Batch batch) throws AuthException, PermissionBackendException {
    for (Batch.Destination dest : batch.listDestinations()) {
      ensureCanSubmit(dest);
    }
  }

  private void ensureCanSubmit(Batch.Destination dest)
      throws AuthException, PermissionBackendException {
    PermissionBackend.ForProject permissions =
        permissionBackend.user(user).project(new Project.NameKey(dest.project));
    permissions.ref(dest.ref).check(RefPermission.FORCE_UPDATE);
  }

  private void submit(Batch batch)
      throws IOException, OrmException, NoSuchProjectException, RepositoryNotFoundException,
          RestApiException, UpdateException, PermissionBackendException {
    for (Batch.Destination dest : batch.listDestinations()) {
      updateRef(dest);
      if (dest.changes != null) {
        closeChanges(dest.changes);
      }
    }
  }

  private void updateRef(Batch.Destination dest)
      throws IOException, NoSuchProjectException, RepositoryNotFoundException {
    Project.NameKey project = new Project.NameKey(dest.project);
    Branch.NameKey branch = new Branch.NameKey(project, dest.ref);
    refUpdater.forceUpdate(branch, ObjectId.fromString(dest.sha1));
  }

  private void closeChanges(Collection<Batch.Change> changes)
      throws IOException, OrmException, RepositoryNotFoundException, RestApiException,
          UpdateException, PermissionBackendException {
    for (Batch.Change change : changes) {
      closeChange(change.toPatchSetId());
    }
  }

  private void closeChange(PatchSet.Id psId)
      throws IOException, OrmException, RepositoryNotFoundException, RestApiException,
          UpdateException, PermissionBackendException {
    ChangeNotes changeNotes = notesFactory.createChecked(psId.getParentKey());
    permissionBackend.user(user).database(db).change(changeNotes).check(ChangePermission.READ);
    Change change = changeNotes.getChange();
    PatchSet ps = psUtil.get(db, changeNotes, psId);
    if (change == null || ps == null) {
      log.error("" + psId + " is missing");
      return;
    }

    if (change.getStatus() == Change.Status.MERGED
        || change.getStatus() == Change.Status.ABANDONED) {
      return;
    }
    Branch.NameKey destination = change.getDest();
    Project.NameKey project = destination.getParentKey();

    try (TraceContext traceContext =
            TraceContext.open()
                .addTag(RequestId.Type.SUBMISSION_ID, new RequestId(change.getId().toString()));
        Repository repo = repoManager.openRepository(project);
        BatchUpdate bu = batchUpdateFactory.create(db, project, user, TimeUtil.nowTs());
        ObjectInserter ins = repo.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk walk = new RevWalk(reader)) {
      bu.setRepository(repo, walk, ins).updateChangesInParallel();
      bu.setRefLogMessage("merged (batch submit)");
      bu.addOp(
          psId.getParentKey(),
          mergedByPushOpFactory.create(requestScopePropagator, psId, destination.get()));
      bu.execute();
    }
  }
}
