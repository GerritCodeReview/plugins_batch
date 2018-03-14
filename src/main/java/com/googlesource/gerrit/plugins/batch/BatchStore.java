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

import com.google.gerrit.server.config.SitePaths;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class BatchStore {
  public static final Charset ENC = StandardCharsets.UTF_8;
  protected Path base;
  protected Gson gson = new Gson();

  @Inject
  public BatchStore(SitePaths site) throws IOException {
    base = site.site_path.resolve("data").resolve("plugin").resolve("batch");
    createDirectories(base);
  }

  /** Returns barebones batch objects for listings */
  public List<Batch> find(boolean includeLastModified) throws IOException {
    List<Batch> batches = new ArrayList<>();
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(base)) {
      for (Path path : directoryStream) {
        Batch batch = read(path, includeLastModified);
        if (batch != null) {
          batches.add(batch);
        }
      }
    }
    return batches;
  }

  protected Batch read(Path path, boolean includeLastModified) {
    File file = path.toFile();
    if (file.isFile()) {
      Batch batch = new Batch(base.relativize(path).toString());
      if (includeLastModified) {
        batch.lastModified = new Date(file.lastModified());
      }
      return batch;
    }
    return null;
  }

  public void save(Batch batch) throws IOException {
    if (batch.state == Batch.State.DELETED) {
      Files.delete(base.resolve(batch.id));
      return;
    }
    String json = gson.toJson(batch);
    if (batch.version != 0) {
      throw new ConcurrentModificationException();
    }
    batch.version++;
    Files.write(base.resolve(batch.id), json.getBytes(ENC));
  }

  /* A drop in replacement for Files.createDirectories that works even when the
   * tail of Path is a link
   */
  protected static Path createDirectories(Path path) throws IOException {
    try {
      return Files.createDirectories(path);
    } catch (FileAlreadyExistsException e) {
      return Files.createDirectories(Files.readSymbolicLink(path));
    }
  }
}
