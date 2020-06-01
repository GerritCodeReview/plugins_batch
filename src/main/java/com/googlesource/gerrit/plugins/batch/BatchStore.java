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
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.FileNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.GitFile;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.util.RefUpdater;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.List;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class BatchStore {
  public static final String BATCHES_REF = "refs/meta/batch/batches/";
  public static final String FILE_NAME = "batch.json";

  public static class AccountIdDeserializer
      implements JsonDeserializer<Account.Id>, JsonSerializer<Account.Id> {

    @Override
    public Account.Id deserialize(
        JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
      if (json.isJsonNull() || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
        throw new JsonParseException("Expected int for account.id type");
      }
      JsonPrimitive p = (JsonPrimitive) json;
      return Account.id(p.getAsInt());
    }

    @Override
    public JsonElement serialize(Account.Id id, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(id.get());
    }
  }

  protected final GitRepositoryManager repoManager;
  protected final Project.NameKey project;
  protected final GitFile.Factory gitFileFactory;
  protected final RefUpdater refUpdater;
  protected final Gson gson =
      new GsonBuilder().registerTypeAdapter(Account.Id.class, new AccountIdDeserializer()).create();

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
  public List<Batch> find(boolean includeBatchInfo) throws IOException {
    List<Batch> batches = new ArrayList<>();
    try (Repository repo = repoManager.openRepository(project)) {
      for (Ref ref : repo.getRefDatabase().getRefsByPrefix(BATCHES_REF)) {
        String id = ref.getName().substring(BATCHES_REF.length());
        try {
          batches.add((includeBatchInfo == true) ? read(id) : new Batch(id));
        } catch (NoSuchBatchException e) {
          continue;
        }
      }
    }
    return batches;
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
      // Fall through
    }
    throw new NoSuchBatchException(id);
  }

  protected FileNameKey getFileNameKey(String id) {
    return getFileNameKey(getBranch(id));
  }

  protected BranchNameKey getBranch(String id) {
    return BranchNameKey.create(project, BATCHES_REF + id);
  }

  protected FileNameKey getFileNameKey(BranchNameKey branch) {
    return FileNameKey.create(branch, FILE_NAME);
  }
}
