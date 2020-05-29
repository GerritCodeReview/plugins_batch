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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.BranchNameKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Data class (for serialization) to represent the contents/state of a batch */
public class Batch {
  public enum State {
    OPEN,
    CLOSED,
    DELETED;
  }

  public static class Change {
    int number;
    int patchSet;

    Change(PatchSet.Id psId) {
      number = psId.changeId().get();
      patchSet = psId.get();
    }

    public PatchSet.Id toPatchSetId() {
      return PatchSet.id(com.google.gerrit.entities.Change.id(number), patchSet);
    }
  }

  public class Destination {
    public String project;
    public String ref;
    public String sha1;
    public String downloadRef;
    public List<Change> changes;

    public void add(PatchSet.Id psId) {
      if (changes == null) {
        changes = new ArrayList<>();
      }
      changes.add(new Batch.Change(psId));
    }
  }

  public final String id;
  public Integer version;
  public Account.Id owner;
  public State state;
  public List<Destination> destinations;
  public Date lastModified;

  public Batch(Account.Id owner) {
    this.id = UUID.randomUUID().toString();
    this.version = 0;
    this.state = State.OPEN;
    this.owner = owner;
  }

  /** Create a barebones entry for listing, not to build with */
  public Batch(String id) {
    this.id = id;
  }

  /** Get a non-null list without modifying the batch. */
  public List<Destination> listDestinations() {
    if (destinations == null) {
      return Collections.emptyList();
    }
    return destinations;
  }

  public Destination getDestination(BranchNameKey branch) {
    if (destinations == null) {
      destinations = new ArrayList<>();
    }
    Destination dest = getExistingDestination(branch);
    if (dest == null) {
      dest = new Destination();
      dest.project = branch.project().get();
      dest.ref = branch.branch();
      destinations.add(dest);
    }
    return dest;
  }

  protected Destination getExistingDestination(BranchNameKey branch) {
    if (destinations == null) {
      destinations = new ArrayList<>();
    }
    for (Destination dest : destinations) {
      if (dest.project.equals(branch.project().get()) && dest.ref.equals(branch.branch())) {
        return dest;
      }
    }
    return null;
  }
}
