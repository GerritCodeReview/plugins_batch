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

import com.googlesource.gerrit.plugins.batch.util.MergeBuilder.FastForwardMode;
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;

import org.eclipse.jgit.util.StringUtils;
import org.kohsuke.args4j.Option;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class FastForwardOptions {
  @Option(name = "--ff", usage = "fast forward update if possible (default)")
  protected boolean ff;

  @Option(name = "--no-ff",
      usage = "create a merge commit even for a fast forward")
  protected boolean noff;

  @Option(name = "--ff-only", usage = "abort unless the merge is a fast forward"
      + " or branch is already up-to-date")
  protected boolean ffOnly;


  private EnumSet<FastForwardMode> selected;

  public FastForwardMode getFastForwardMode() throws UnloggedFailure {
    if (selected == null) {
      EnumMap<FastForwardMode, Boolean> valuesByMode =
          new EnumMap<FastForwardMode, Boolean>(FastForwardMode.class);
      valuesByMode.put(FastForwardMode.FF, ff);
      valuesByMode.put(FastForwardMode.NO_FF, noff);
      valuesByMode.put(FastForwardMode.FF_ONLY, ffOnly);

      selected = EnumSet.noneOf(FastForwardMode.class);
      for (FastForwardMode mode : valuesByMode.keySet()) {
        if (valuesByMode.get(mode)) {
          selected.add(mode);
        }
      }
    }
    if (selected.size() > 1) {
      throw new UnloggedFailure(1, StringUtils.join(toStrings(
          selected), ", ") + " are mutually exclusive");
    }
    if (selected.size() == 1) {
      return selected.toArray(new FastForwardMode[1])[0];
    }
    return null;
  }

  private static Set<String> toStrings(EnumSet<FastForwardMode> modes) {
    Set<String> out = new HashSet<String>();
    for (FastForwardMode mode : modes) {
      out.add(mode.getName());
    }
    return out;
  }
}
