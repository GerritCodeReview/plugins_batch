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

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.git.meta.GitFile;
import com.google.inject.internal.UniqueAnnotations;
import com.googlesource.gerrit.plugins.batch.util.MergeBranch;
import com.googlesource.gerrit.plugins.batch.util.MergeBuilder;

public class Module extends FactoryModule {
  @Override
  protected void configure() {
    factory(GitFile.Factory.class);
    factory(MergeBranch.Factory.class);
    factory(MergeBuilder.Factory.class);
    factory(GitFile.Factory.class);
    factory(MergeChange.Factory.class);

    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(BatchCleaner.Lifecycle.class);
  }
}
