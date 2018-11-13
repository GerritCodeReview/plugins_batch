// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.git.meta;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.File;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/** A GitFile is a text file (UTF8) from a git repository */
public class GitFile extends VersionedMetaData {
  public interface Factory {
    GitFile create(@Assisted File.NameKey file);
  }

  protected final MetaDataUpdate.User metaDataUpdateFactory;
  protected final GitRepositoryManager repos;

  protected Branch.NameKey branch;
  protected String file;

  public String text;

  @Inject
  public GitFile(
      MetaDataUpdate.User metaDataUpdateFactory,
      GitRepositoryManager repos,
      @Assisted File.NameKey file) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.repos = repos;
    this.branch = file.getParentKey();
    this.file = file.get();
  }

  public String read() throws ConfigInvalidException, IOException, NoSuchProjectException {
    try (Repository repo = repos.openRepository(branch.getParentKey())) {
      load(repo);
      return text;
    }
  }

  public RevCommit write(String fileContent, String commitMessage)
      throws ConfigInvalidException, IOException, NoSuchProjectException {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(branch.getParentKey())) {
      load(md);
      text = fileContent;
      md.getCommitBuilder().setCommitter(metaDataUpdateFactory.getUserPersonIdent());
      md.setMessage(commitMessage);
      return commit(md);
    }
  }

  public void setBranch(Branch.NameKey branch) {
    this.branch = branch;
  }

  public void setFileName(String fileName) {
    this.file = fileName;
  }

  @Override
  protected String getRefName() {
    return branch.get();
  }

  @Override
  protected void onLoad() throws IOException {
    text = readUTF8(file);
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException {
    saveUTF8(file, text);
    return true;
  }
}
