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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.StringKey;

public class File {
  /** An immutable reference to a file in gerrit repo. */
  public static class NameKey extends StringKey<Branch.NameKey> {
    private static final long serialVersionUID = 1L;

    protected Branch.NameKey branch;
    protected String fileName;

    protected NameKey() {
      branch = new Branch.NameKey(new Project.NameKey(null), null);
    }

    public NameKey(Branch.NameKey br, String file) {
      branch = br;
      fileName = file;
    }

    @Override
    public String get() {
      return fileName;
    }

    @Override
    protected void set(String file) {
      fileName = file;
    }

    @Override
    public Branch.NameKey getParentKey() {
      return branch;
    }
  }
}
