// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.entities;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;

public class File {
  /** An immutable reference to a file in gerrit repo. */
  public static class NameKey implements Comparable<NameKey> {
    protected BranchNameKey branch;
    protected String fileName;

    protected NameKey() {
      branch = BranchNameKey.create(Project.nameKey(null), null);
    }

    public NameKey(BranchNameKey br, String file) {
      branch = br;
      fileName = file;
    }

    public String get() {
      return fileName;
    }

    public BranchNameKey getParentKey() {
      return branch;
    }

    @Override
    public final int compareTo(NameKey o) {
      return get().compareTo(o.get());
    }
  }
}
