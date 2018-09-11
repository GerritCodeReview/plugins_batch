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
import com.google.gerrit.reviewdb.client.File;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.GitFile;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.util.RefUpdater;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.Date;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class BatchStore {
  public static final String BATCHES_REF = "refs/meta/batch/batches/";
  public static final String FILE_NAME = "batch.json";

  protected final Project.NameKey project;
  protected final GitFile.Factory gitFileFactory;
  protected final RefUpdater refUpdater;
  protected final Gson gson = new Gson();

  @Inject
  public BatchStore(AllProjectsName project, GitFile.Factory gitFileFactory, RefUpdater refUpdater)
      throws IOException {
    this.project = project;
    this.gitFileFactory = gitFileFactory;
    this.refUpdater = refUpdater;
  }

  public void save(Batch batch) throws IOException, NoSuchProjectException {
    if (batch.state == Batch.State.DELETED) {
      refUpdater.delete(getBranch(batch.id));
      return;
    }
    if (batch.version != 0) {
      throw new ConcurrentModificationException();
    }
    batch.version++;
    batch.lastModified = new Date();
    try {
      gitFileFactory
          .create(getFileNameKey(batch.id))
          .write(gson.toJson(batch), "Batch created (batch plugin)");
    } catch (ConfigInvalidException e) { // Not real, never going to be thrown
      throw new RuntimeException(e);
    }
  }

  public Batch read(String id) throws IOException, NoSuchBatchException {
    try {
      Batch batch = gson.fromJson(gitFileFactory.create(getFileNameKey(id)).read(), Batch.class);
      if (batch != null) { // gson.fromJson() can return null without throwing an exception
        return batch;
      }
    } catch (ConfigInvalidException e) { // Not real, never going to be thrown
      throw new RuntimeException(e);
    } catch (NoSuchProjectException e) {
    }
    throw new NoSuchBatchException(id);
  }

  protected File.NameKey getFileNameKey(String id) {
    return getFileNameKey(getBranch(id));
  }

  protected Branch.NameKey getBranch(String id) {
    return new Branch.NameKey(project, BATCHES_REF + id);
  }

  protected File.NameKey getFileNameKey(Branch.NameKey branch) {
    return new File.NameKey(branch, FILE_NAME);
  }
}
