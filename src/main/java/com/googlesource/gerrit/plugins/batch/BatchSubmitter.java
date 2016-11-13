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

import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import com.googlesource.gerrit.plugins.batch.exception.InvalidBatchOperationException;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.MergedByPushOp;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.util.RefUpdater;
import com.google.gerrit.server.util.RequestId;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;

public class BatchSubmitter {
  private static final Logger log =
      LoggerFactory.getLogger(BatchSubmitter.class);

  private ReviewDb db;
  private GitRepositoryManager repoManager;
  private ProjectControl.Factory projectControlFactory;
  private ChangeControl.GenericFactory changeControlFactory;
  private RefUpdater refUpdater;
  private BatchRemover remover;
  private RequestScopePropagator requestScopePropagator;
  private IdentifiedUser user;
  private BatchStore store;
  private BatchUpdate.Factory batchUpdateFactory;
  private MergedByPushOp.Factory mergedByPushOpFactory;

  @Inject
  BatchSubmitter(ReviewDb db,
      GitRepositoryManager repoManager,
      ProjectControl.Factory projectControlFactory,
      ChangeControl.GenericFactory changeControlFactory,
      RefUpdater refUpdater,
      RequestScopePropagator requestScopePropagator,
      BatchUpdate.Factory batchUpdateFactory,
      MergedByPushOp.Factory mergedByPushOpFactory, IdentifiedUser user,
      BatchStore store, BatchRemover remover) {
    this.db = db;
    this.repoManager = repoManager;
    this.projectControlFactory = projectControlFactory;
    this.changeControlFactory = changeControlFactory;
    this.refUpdater = refUpdater;
    this.requestScopePropagator = requestScopePropagator;
    this.batchUpdateFactory = batchUpdateFactory;
    this.mergedByPushOpFactory = mergedByPushOpFactory;
    this.user = user;
    this.store = store;
    this.remover = remover;
  }

  public Batch submit(String id) throws IOException,
      InvalidBatchOperationException, NoSuchBatchException,
      NoSuchProjectException, OrmException, PermissionDeniedException,
      RestApiException, RepositoryNotFoundException, UpdateException {
    Batch batch = store.read(id);
    if (batch.state == Batch.State.OPEN) {
      throw new InvalidBatchOperationException(id, batch.state);
    }
    ensureCanSubmit(batch);
    submit(batch);
    remover.remove(batch);
    return batch;
  }

  private void ensureCanSubmit(Batch batch)
      throws PermissionDeniedException, NoSuchProjectException {
    for (Batch.Destination dest : batch.listDestinations()) {
      ensureCanSubmit(dest);
    }
  }

  private void ensureCanSubmit(Batch.Destination dest)
      throws PermissionDeniedException, NoSuchProjectException {
    Project.NameKey project = new Project.NameKey(dest.project);
    ProjectControl pctl = projectControlFactory.validateFor(project);

    Branch.NameKey branch = new Branch.NameKey(project, dest.ref);
    RefControl rctl = pctl.controlForRef(branch);
    if (!rctl.canForceUpdate()) {
      throw new PermissionDeniedException(
          "Permission denied: cannot forceUpdate " + branch);
    }
  }

  private void submit(Batch batch)
      throws IOException, OrmException, RepositoryNotFoundException,
      RestApiException, UpdateException {
    for (Batch.Destination dest : batch.listDestinations()) {
      updateRef(dest);
      if (dest.changes != null) {
        closeChanges(dest.changes);
      }
    }
  }

  private void updateRef(Batch.Destination dest) throws IOException,
      RepositoryNotFoundException {
    Project.NameKey project = new Project.NameKey(dest.project);
    Branch.NameKey branch = new Branch.NameKey(project, dest.ref);
    refUpdater.forceUpdate(branch, ObjectId.fromString(dest.sha1));
  }

  private void closeChanges(Collection<Batch.Change> changes)
      throws IOException, OrmException, RepositoryNotFoundException,
      RestApiException, UpdateException {
    for (Batch.Change change : changes) {
      closeChange(change.toPatchSetId());
    }
  }

  private void closeChange(PatchSet.Id psId) throws IOException, OrmException,
      RepositoryNotFoundException, RestApiException, UpdateException {
    Change.Id cid = psId.getParentKey();
    Change change = db.changes().get(cid);
    PatchSet ps = db.patchSets().get(psId);
    if (change == null || ps == null) {
      log.error("" + psId + " is missing");
      return;
    }

    if (change.getStatus() == Change.Status.MERGED ||
        change.getStatus() == Change.Status.ABANDONED) {
      return;
    }
    Branch.NameKey destination = change.getDest();
    Project.NameKey project = destination.getParentKey();

    try (Repository repo = repoManager.openRepository(project);
        RevWalk walk = new RevWalk(repo);
        BatchUpdate bu = batchUpdateFactory.create(db,
            project, user, TimeUtil.nowTs());
        ObjectInserter ins = repo.newObjectInserter()) {
      bu.setRepository(repo, walk, ins).updateChangesInParallel();
      bu.setRequestId(RequestId.forChange(change));

      bu.addOp(psId.getParentKey(),
        mergedByPushOpFactory.create(requestScopePropagator, psId,
            destination.get()));
      bu.execute();
    }
  }
}
