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
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.util.cli.Options;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.MergeChange;
import com.googlesource.gerrit.plugins.batch.api.extensions.MergeInput;
import com.googlesource.gerrit.plugins.batch.cli.FastForwardOptions;
import com.googlesource.gerrit.plugins.batch.cli.MergeStrategyOption;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(
  name = "merge-change",
  description = "Merge changes in the git repository to a batch"
)
public class MergeChangeCommand extends SshCommand {
  @Inject @Options public MergeStrategyOption strategy;
  @Inject @Options public FastForwardOptions fastForward;
  @Inject protected MergeChange.Factory mergeChangeFactory;
  public MergeInput mergeInput = new MergeInput();

  @Option(
    name = "--message",
    aliases = "-m",
    metaVar = "MESSAGE",
    usage = "commit message to use when applying a change"
  )
  protected void setMessage(String message) {
    mergeInput.batchInput.message = message;
  }

  @Option(name = "--close", usage = "close batch on merge success")
  protected void setClose(Boolean close) {
    mergeInput.batchInput.close = close;
  }

  @Argument(
    index = 0,
    required = true,
    multiValued = true,
    metaVar = "{CHANGE,PATCHSET}",
    usage = "list of patch sets to merge"
  )
  protected void getPatchSetId(String token) {
    mergeInput.mergeChangeInput.patchSets.add(token);
  }

  @Override
  public void run() throws Exception {
    parseCommandLine();
    MergeChange mergeChange = mergeChangeFactory.create(mergeInput);
    out.write((OutputFormat.JSON.newGson().toJson(mergeChange.createBatch()) + "\n").getBytes(ENC));
    out.flush();
  }
}
