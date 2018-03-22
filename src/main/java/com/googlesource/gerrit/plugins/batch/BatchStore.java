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
import com.google.gerrit.server.submit.IntegrationException;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ConcurrentModificationException;
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

  public Batch read(String id) throws IOException, IntegrationException {
    try {
      return gson.fromJson(readFile(base.resolve(id)), Batch.class);
    } catch (NoSuchFileException e) {
      throw new IntegrationException(id);
    }
  }

  protected static String readFile(Path file) throws IOException {
    StringBuilder buffer = new StringBuilder();
    for (String line : Files.readAllLines(file, ENC)) {
      buffer.append(line);
    }
    return buffer.toString();
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
