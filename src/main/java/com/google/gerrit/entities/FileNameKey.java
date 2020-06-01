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

import com.google.auto.value.AutoValue;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;

/** An immutable reference to a file in gerrit repo. */
@AutoValue
public abstract class FileNameKey implements Comparable<FileNameKey> {
  public static FileNameKey create(Branch.NameKey branch, String file) {
    return new AutoValue_FileNameKey(branch, file);
  }

  public static FileNameKey create() {
    return new AutoValue_FileNameKey(new Branch.NameKey(new Project.NameKey(null), null), null);
  }

  public abstract Branch.NameKey branch();
  public abstract String file();

  @Override
  public final int compareTo(FileNameKey o) {
    return file().compareTo(o.file());
  }
}
