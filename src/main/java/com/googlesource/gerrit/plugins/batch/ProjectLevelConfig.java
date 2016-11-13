// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.server.git.VersionedMetaData;

import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.project.ProjectState;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/** Configuration file in the projects refs/meta/config branch. */
public class ProjectLevelConfig extends VersionedMetaData {
  private final String fileName;
  private final ProjectState project;
  private Config cfg;

  public ProjectLevelConfig(String fileName, ProjectState project) {
    this.fileName = fileName;
    this.project = project;
  }

  @Override
  protected String getRefName() {
    return "refs/meta/config"; //RefNames.REFS_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    cfg = readConfig(fileName);
  }

  public Config get() {
    if (cfg == null) {
      cfg = new Config();
    }
    return cfg;
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException {
    if (commit.getMessage() == null || "".equals(commit.getMessage())) {
      commit.setMessage("Updated configuration\n");
    }
    saveConfig(fileName, cfg);
    return true;
  }
}
