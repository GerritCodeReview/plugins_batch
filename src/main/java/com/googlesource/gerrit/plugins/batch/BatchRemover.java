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

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.util.RefUpdater;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

public class BatchRemover {
  protected final RefUpdater refUpdater;
  protected final BatchStore store;

  @Inject
  protected BatchRemover(RefUpdater refUpdater, BatchStore store) {
    this.refUpdater = refUpdater;
    this.store = store;
  }

  public Batch remove(String id)
      throws IllegalStateException, NoSuchBatchException, IOException, RepositoryNotFoundException,
          NoSuchProjectException {
    return remove(store.read(id));
  }

  public Batch remove(Batch batch)
      throws IOException, IllegalStateException, RepositoryNotFoundException,
          NoSuchProjectException {
    if (batch.state == Batch.State.OPEN) {
      throw new IllegalStateException(
          "Invalid Operation for Batch(" + batch.id + "): " + batch.state.toString());
    }
    removeDownloadRefs(batch);
    batch.state = Batch.State.DELETED;
    store.save(batch);
    return batch;
  }

  protected void removeDownloadRefs(Batch batch)
      throws IOException, RepositoryNotFoundException, NoSuchProjectException {
    for (Batch.Destination dest : batch.listDestinations()) {
      Project.NameKey project = Project.nameKey(dest.project);
      BranchNameKey branch = BranchNameKey.create(project, dest.downloadRef);
      refUpdater.delete(branch);
    }
  }
}
