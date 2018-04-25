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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.GitFile;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.util.RefUpdater;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class BatchStore {
  public static final String BATCHES_REF = "refs/meta/batch/batches/";
  public static final String FILE_NAME = "batch.json";

  protected final GitRepositoryManager repoManager;
  protected final Project.NameKey project;
  protected final GitFile.Factory gitFileFactory;
  protected final RefUpdater refUpdater;
  protected final Gson gson = new Gson();

  @Inject
  public BatchStore(
      GitRepositoryManager repoManager,
      AllProjectsName project,
      GitFile.Factory gitFileFactory,
      RefUpdater refUpdater) {
    this.repoManager = repoManager;
    this.project = project;
    this.gitFileFactory = gitFileFactory;
    this.refUpdater = refUpdater;
  }

  /** Returns a list of batch objects */
  public List<Batch> find(boolean includeBatchInfo) throws IOException, NoSuchBatchException {
    List<Batch> batches = new ArrayList<>();
    try (Repository repo = repoManager.openRepository(project)) {
      for (Map.Entry<String, Ref> entry : repo.getRefDatabase().getRefs(BATCHES_REF).entrySet()) {
        Batch batch = new Batch(entry.getKey());
        if (includeBatchInfo) {
          batch = read(entry.getKey());
        }
        batches.add(batch);
      }
    }
    return batches;
  }

  public void save(Batch batch) throws IOException, NoSuchProjectException {
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

  public Batch read(String id) throws NoSuchBatchException {
    try {
      Batch batch = gson.fromJson(gitFileFactory.create(getFileNameKey(id)).read(), Batch.class);
      // null check as gson.fromJson() will hand back a null object without throwing an exception
      if (batch != null) {
        return batch;
      }
    } catch (Exception e) {
      // this is empty to handle both exceptions and null object in the same throw
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
