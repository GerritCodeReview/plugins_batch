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
package com.googlesource.gerrit.plugins.batch.ssh;

import com.google.gerrit.server.git.meta.GitFile;
import com.google.gerrit.sshd.PluginCommandModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

public class SshModule extends PluginCommandModule {
  @Override
  protected void configureCommands() {
    install(new FactoryModuleBuilder().build(GitFile.Factory.class));

    command(MergeChangeCommand.class);
    command(ListCommand.class);
  }
}
