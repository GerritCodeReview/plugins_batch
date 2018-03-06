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

package com.googlesource.gerrit.plugins.batch.cli;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

public class PatchSetArgument {
  public static class Factory {
    private final PermissionBackend permissionBackend;
    private final ChangeNotes.Factory notesFactory;
    private final ReviewDb reviewDb;
    private final PatchSetUtil psUtil;
    private CurrentUser user;

    @Inject
    protected Factory(
        ChangeNotes.Factory notesFactory,
        PermissionBackend permissionBackend,
        ReviewDb reviewDb,
        PatchSetUtil psUtil,
        CurrentUser user) {
      this.notesFactory = notesFactory;
      this.permissionBackend = permissionBackend;
      this.reviewDb = reviewDb;
      this.psUtil = psUtil;
      this.user = user;
    }

    public PatchSetArgument createForArgument(String token) {
      try {
        PatchSet.Id patchSetId = parsePatchSet(token);
        ChangeNotes changeNotes = notesFactory.createChecked(patchSetId.getParentKey());
        permissionBackend
            .user(user)
            .database(reviewDb)
            .change(changeNotes)
            .check(ChangePermission.READ);
        return new PatchSetArgument(
            changeNotes.getChange(), psUtil.get(reviewDb, changeNotes, patchSetId));
      } catch (PermissionBackendException | AuthException e) {
        throw new IllegalArgumentException("database error", e);

      } catch (UnloggedFailure e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      } catch (OrmException e) {
        throw new IllegalArgumentException("database error", e);
      }
    }

    private PatchSet.Id parsePatchSet(String patchIdentity) throws UnloggedFailure, OrmException {
      // By older style change,patchset
      if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
        try {
          return PatchSet.Id.parse(patchIdentity);
        } catch (IllegalArgumentException e) {
          throw error("\"" + patchIdentity + "\" is not a valid patch set");
        }
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
