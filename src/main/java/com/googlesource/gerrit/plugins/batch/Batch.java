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

package com.googlesource.gerrit.plugins.batch;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
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
      number = psId.getParentKey().get();
      patchSet = psId.get();
    }

    public PatchSet.Id toPatchSetId() {
      return new PatchSet.Id(new com.google.gerrit.reviewdb.client.Change.Id(number), patchSet);
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
        changes = new ArrayList<Change>();
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

  public Destination getDestination(Branch.NameKey branch) {
    if (destinations == null) {
      destinations = new ArrayList<Destination>();
    }
    Destination dest = getExistingDestination(branch);
    if (dest == null) {
      dest = new Destination();
      dest.project = branch.getParentKey().get();
      dest.ref = branch.get();
      destinations.add(dest);
    }
    return dest;
  }

  private Destination getExistingDestination(Branch.NameKey branch) {
    if (destinations == null) {
      destinations = new ArrayList<Destination>();
    }
    for (Destination dest : destinations) {
      if (dest.project.equals(branch.getParentKey().get()) && dest.ref.equals(branch.get())) {
        return dest;
      }
    }
    return null;
  }
}
