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

import com.googlesource.gerrit.plugins.batch.exception.InvalidBatchOperationException;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import com.google.gerrit.server.util.RefUpdater;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.IOException;

public class BatchRemover {
  private RefUpdater refUpdater;
  private BatchStore store;

  @Inject
  BatchRemover(RefUpdater refUpdater, BatchStore store) {
    this.refUpdater = refUpdater;
    this.store = store;
  }

  public Batch remove(String id) throws InvalidBatchOperationException,
      NoSuchBatchException, IOException, RepositoryNotFoundException {
    return remove(store.read(id));
  }

  public Batch remove(Batch batch) throws IOException,
      InvalidBatchOperationException, RepositoryNotFoundException {
    if (batch.state == Batch.State.OPEN) {
      throw new InvalidBatchOperationException(batch.id, batch.state);
    }
    removeDownloadRefs(batch);
    batch.state = Batch.State.DELETED;
    store.save(batch);
    return batch;
  }

  private void removeDownloadRefs(Batch batch) throws IOException,
      RepositoryNotFoundException {
    for (Batch.Destination dest : batch.listDestinations()) {
      Project.NameKey project = new Project.NameKey(dest.project);
      Branch.NameKey branch = new Branch.NameKey(project, dest.downloadRef);
      refUpdater.delete(branch);
    }
  }
}
