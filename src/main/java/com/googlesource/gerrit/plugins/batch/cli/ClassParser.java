// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.util.cli.CmdLineParser;
import java.lang.reflect.Field;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.Setters;

public class ClassParser {
  public void parse(Object bean, CmdLineParser parser) {
    for (Class c = bean.getClass(); c != null; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        Option o = f.getAnnotation(Option.class);
        if (o != null) {
          parser.addOption(Setters.create(f, bean), o);
        }
        Argument a = f.getAnnotation(Argument.class);
        if (a != null) {
          parser.addArgument(Setters.create(f, bean), a);
        }
      }
    }
  }
}
