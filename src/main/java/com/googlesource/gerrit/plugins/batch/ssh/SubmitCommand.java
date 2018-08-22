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

import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.Batch;
import com.googlesource.gerrit.plugins.batch.BatchSubmitter;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(
    name = "submit",
    description = "Submit the batch updates to the destination branches")
public class SubmitCommand extends SshCommand {
  @Option(name = "--force", required = true, usage = "force push the batch updates")
  protected boolean force;

  @Argument(metaVar = "BATCH-ID", usage = "id of the batch to submit")
  protected String batchId;

  @Inject protected BatchSubmitter impl;

  @Override
  public void run() throws Exception {
    parseCommandLine();
    try {
      Batch batch = impl.submit(batchId);
      out.write((OutputFormat.JSON.newGson().toJson(batch) + "\n").getBytes(ENC));
    } catch (NoSuchBatchException | IllegalStateException | PermissionBackendException e) {
      throw new UnloggedFailure(1, e.getMessage());
    }
    out.flush();
  }
}
