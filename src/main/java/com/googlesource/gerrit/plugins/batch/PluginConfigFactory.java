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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginConfigFactory {
  private static final Logger log = LoggerFactory.getLogger(Config.class);

  public static final String EXTENSION = ".config";

  protected final GitRepositoryManager repoManager;
  protected final ProjectCache projectCache;
  protected final ProjectState.Factory projectStateFactory;

  protected static Config config;

  @Inject
  protected PluginConfigFactory(
      GitRepositoryManager repoManager,
      ProjectCache projectCache,
      ProjectState.Factory projectStateFactory) {
    this.repoManager = repoManager;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
  }

  public Config getConfig() throws NoSuchProjectException {
    if (config == null) {
      config = getProjectPluginConfig(projectCache.getAllProjects(), "batch");
    }
    return config;
  }
  /**
   * Returns the configuration for the specified plugin that is stored in the '{@code
   * <plugin-name>.config}' file in the 'refs/meta/config' branch of the specified project.
   *
   * @param projectState the project for which the plugin configuration should be returned
   * @param pluginName the name of the plugin for which the configuration should be returned
   * @return the plugin configuration from the '{@code <plugin-name>.config}' file of the specified
   *     project
   */
  public Config getProjectPluginConfig(ProjectState projectState, String pluginName) {
    return getConfig(projectState, pluginName + EXTENSION).get();
  }

  public ProjectLevelConfig getConfig(ProjectState state, String fileName) {
    Project project = state.getProject();
    ProjectLevelConfig cfg = new ProjectLevelConfig(fileName, state);
    try (Repository repo = repoManager.openRepository(project.getNameKey())) {
      cfg.load(repo);
    } catch (IOException | ConfigInvalidException e) {
      log.warn("Failed to load " + fileName + " for " + project.getName(), e);
    }
    return cfg;
  }
}
