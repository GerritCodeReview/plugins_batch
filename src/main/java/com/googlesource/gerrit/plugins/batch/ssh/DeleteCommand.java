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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.Batch;
import com.googlesource.gerrit.plugins.batch.BatchRemover;
import com.googlesource.gerrit.plugins.batch.exception.NoSuchBatchException;
import org.kohsuke.args4j.Argument;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "delete", description = "Delete a batch")
public class DeleteCommand extends SshCommand {
  @Argument(metaVar = "BATCH-ID", usage = "id of the batch to delete")
  protected String batchId;

  @Inject protected BatchRemover impl;

  @Override
  public void run() throws Exception {
    try {
      Batch batch = impl.remove(batchId);
      out.write((OutputFormat.JSON.newGson().toJson(batch) + "\n").getBytes(ENC));
    } catch (NoSuchBatchException | IllegalStateException e) {
      throw new UnloggedFailure(1, e.getMessage());
    }
    out.flush();
  }
}
