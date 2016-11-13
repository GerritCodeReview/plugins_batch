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

package com.googlesource.gerrit.plugins.batch.cli;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.inject.Inject;
import com.google.gwtorm.server.OrmException;

public class PatchSetArgument {
  public static class Factory {
    private ReviewDb db;
    private ChangeControl.GenericFactory ccFactory;
    private CurrentUser user;

    @Inject
    protected Factory(ReviewDb db, ChangeControl.GenericFactory ccFactory,
        CurrentUser user) {
      this.db = db;
      this.ccFactory = ccFactory;
      this.user = user;
    }

    public PatchSetArgument createForArgument(String token) {
      try {
        PatchSet ps = parsePatchSet(token);
        Change change = db.changes().get(ps.getId().getParentKey());
        ccFactory.validateFor(db, change.getId(), user);
        return new PatchSetArgument(change, ps);
      } catch (UnloggedFailure e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      } catch (OrmException e) {
        throw new IllegalArgumentException("database error", e);
      } catch (NoSuchChangeException e) {
        throw new IllegalArgumentException(noSuchPatchSet(token), e);
      }
    }

    private PatchSet parsePatchSet(String patchIdentity)
        throws UnloggedFailure, OrmException {
      // By older style change,patchset
      if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
        PatchSet.Id patchSetId;
        try {
          patchSetId = PatchSet.Id.parse(patchIdentity);
        } catch (IllegalArgumentException e) {
          throw error("\"" + patchIdentity + "\" is not a valid patch set");
        }
        PatchSet patchSet = db.patchSets().get(patchSetId);
        if (patchSet == null) {
          throw error(noSuchPatchSet(patchIdentity));
        }
        return patchSet;
      }

      throw error("\"" + patchIdentity + "\" is not a valid patch set");
    }

    private String noSuchPatchSet(String patchIdentity) {
      return "\"" + patchIdentity + "\" no such patch set";
    }

    private static UnloggedFailure error(final String msg) {
      return new UnloggedFailure(1, msg);
    }
  }

  public final PatchSet patchSet;
  public final Change change;

  public PatchSetArgument(Change change, PatchSet patchSet) {
    this.patchSet = patchSet;
    this.change = change;
  }

  public void ensureLatest() {
    if (!change.currentPatchSetId().equals(patchSet.getId())) {
      throw new IllegalArgumentException(patchSet + " is not the latest patch set");
    }
  }
}
