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
package com.googlesource.gerrit.plugins.batch.cli;

import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import org.eclipse.jgit.merge.MergeStrategy;
import org.kohsuke.args4j.Option;

public class MergeStrategyOption {
  @Option(
    name = "--strategy",
    metaVar = "STRATEGY",
    usage = "jgit merge strategy(ours|theirs|simple[-two-way-in-core]|resolve) to use"
  )
  protected String strategy;

  protected MergeStrategy mergeStrategy;

  public MergeStrategy getMergeStrategy() throws UnloggedFailure {
    if (mergeStrategy == null && strategy != null) {
      if ("simple".equals(strategy)) {
        strategy = "simple-two-way-in-core";
      }
      mergeStrategy = MergeStrategy.get(strategy);
      if (mergeStrategy == null) {
        throw new UnloggedFailure(1, "unknown strategy " + strategy);
      }
    }
    return mergeStrategy;
  }
}
