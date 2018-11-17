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

import com.google.gerrit.server.AccessPath;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.ListBatches;
import org.apache.sshd.server.Environment;

@CommandMetaData(name = "ls-batches", description = "List batches visible to caller")
public class ListCommand extends BaseCommand {
  protected final ListBatches impl;

  @Inject
  ListCommand(ListBatches impl) {
    this.impl = impl;
  }

  @Override
  public void start(final Environment env) {
    startThread(
        new CommandRunnable() {
          @Override
          public void run() throws Exception {
            parseCommandLine(impl);
            impl.display(out);
          }
        },
        AccessPath.SSH_COMMAND);
  }
}
