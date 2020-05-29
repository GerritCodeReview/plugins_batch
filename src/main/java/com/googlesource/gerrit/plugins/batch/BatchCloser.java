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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.util.RefUpdater;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;

public class BatchCloser {
  protected final RefUpdater refUpdater;
  protected final IdentifiedUser user;
  protected final BatchStore store;

  @Inject
  BatchCloser(RefUpdater refUpdater, IdentifiedUser user, BatchStore store) {
    this.refUpdater = refUpdater;
    this.user = user;
    this.store = store;
  }

  public void close(Batch batch) throws IOException, IllegalStateException, NoSuchProjectException {
    if (batch.state != Batch.State.OPEN) {
      throw new IllegalStateException(
          "Invalid Operation for Batch(" + batch.id + "): " + batch.state.toString());
    }
    createDownloadRefs(batch);
    batch.state = Batch.State.CLOSED;
    store.save(batch);
  }

  protected void createDownloadRefs(Batch batch) throws IOException, NoSuchProjectException {
    for (Batch.Destination dest : batch.listDestinations()) {
      dest.downloadRef = getBatchRef(batch, dest);
      Project.NameKey project = new Project.NameKey(dest.project);
      Branch.NameKey branch = new Branch.NameKey(project, dest.downloadRef);
      ObjectId id = ObjectId.fromString(dest.sha1);
      refUpdater.update(branch, ObjectId.zeroId(), id);
    }
  }

  protected String getBatchRef(Batch batch, Batch.Destination dest) {
    // AccountId is always present, UserName is optional but the preferred identifier
    if (user.getUserName().isPresent()) {
      return String.format(
          "refs/batch/%s/%s/%s/%s", "users", user.getUserName().get(), batch.id, dest.ref);
    }
    return String.format(
        "refs/batch/%s/%s/%s/%s",
        "accounts", String.valueOf(user.getAccountId().get()), batch.id, dest.ref);
  }
}
