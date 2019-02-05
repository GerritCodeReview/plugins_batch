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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.util.cli.Options;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.googlesource.gerrit.plugins.batch.Batch;
import com.googlesource.gerrit.plugins.batch.BatchCloser;
import com.googlesource.gerrit.plugins.batch.cli.FastForwardOptions;
import com.googlesource.gerrit.plugins.batch.cli.MergeStrategyOption;
import com.googlesource.gerrit.plugins.batch.cli.PatchSetArgument;
import com.googlesource.gerrit.plugins.batch.util.MergeBranch;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(
    name = "merge-change",
    description = "Merge changes in the git repository to a batch")
public class MergeChangeCommand extends SshCommand {
  @Inject @Options public MergeStrategyOption strategy;
  @Inject @Options public FastForwardOptions fastForward;

  @Option(
      name = "--message",
      aliases = "-m",
      metaVar = "MESSAGE",
      usage = "commit message to use when applying a change")
  public String message;

  @Option(name = "--close", usage = "close batch on merge success")
  public boolean close;

  protected LinkedHashMap<PatchSet.Id, PatchSetArgument> patchSetArgumentsByPatchSet =
      new LinkedHashMap<>();

  @Argument(
      index = 0,
      required = true,
      multiValued = true,
      metaVar = "{CHANGE,PATCHSET}",
      usage = "list of patch sets to merge")
  protected void addPatchSetId(final String token) {
    PatchSetArgument psa = patchSetArgumentFactory.createForArgument(token);
    psa.ensureLatest();
    patchSetArgumentsByPatchSet.put(psa.patchSet.getId(), psa);
  }

  @Inject protected PatchSetArgument.Factory patchSetArgumentFactory;
  @Inject protected MergeBranch.Factory mergeBranchFactory;
  @Inject protected BatchCloser batchCloser;
  @Inject protected ReviewDb db;
  @Inject protected IdentifiedUser user;
  @Inject protected GitRepositoryManager repoManager;
  protected Map<PatchSet.Id, List<ObjectId>> parentsByPsarg = new HashMap<>();

  @Override
  public void run() throws Exception {
    parseCommandLine();
    Batch batch = new Batch(user.getAccountId());
    String err = null;
    try {
      Resolver resolver = new Resolver(patchSetArgumentsByPatchSet.values());
      for (PatchSetArgument psarg : resolver.resolved) {
        err = "Couldn't merge change(" + psarg.patchSet + ") to batch(" + batch.id + ")";
        merge(batch, psarg.change, psarg.patchSet);
      }
      if (close) {
        err = "Could not close batch(" + batch.id + ")";
        batchCloser.close(batch);
      }
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg != null) {
        err += ": " + msg;
      }
      throw die(err);
    }
    batch.version = null;
    out.write((OutputFormat.JSON.newGson().toJson(batch) + "\n").getBytes(ENC));
    out.flush();
  }

  protected boolean isParentMergedInto(PatchSetArgument psarg, Iterable<ObjectId> sha1s)
      throws IOException, OrmException, RepositoryNotFoundException {
    for (ObjectId sha1 : sha1s) {
      if (isParentMergedInto(psarg, sha1)) {
        return true;
      }
    }
    return false;
  }

  protected boolean isParentMergedInto(PatchSetArgument psarg, ObjectId sha1)
      throws IOException, OrmException, RepositoryNotFoundException {
    List<ObjectId> parents = getParents(psarg);
    if (parents.isEmpty()) {
      return true;
    }
    for (ObjectId parent : parents) {
      Project.NameKey project = psarg.change.getDest().getParentKey();
      if (isMergedInto(project, parent, sha1)) {
        return true;
      }
    }
    return false;
  }

  public boolean isMergedInto(Project.NameKey project, ObjectId needle, ObjectId haystack)
      throws IOException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk walk = new RevWalk(repo)) {
      return walk.isMergedInto(walk.parseCommit(needle), walk.parseCommit(haystack));
    }
  }

  protected ObjectId getTip(Branch.NameKey branch)
      throws IOException, NoSuchRefException, RepositoryNotFoundException {
    try (Repository repo = repoManager.openRepository(branch.getParentKey())) {
      Ref ref = repo.getRefDatabase().getRef(branch.get());
      if (ref == null) {
        throw new NoSuchRefException(branch.toString());
      }
      return ref.getObjectId();
    }
  }

  protected void merge(Batch batch, Change change, PatchSet ps)
      throws Exception, IOException, NoSuchRefException, OrmException, UnloggedFailure {
    Branch.NameKey branch = change.getDest();
    Batch.Destination dest = batch.getDestination(branch);
    dest.sha1 =
        mergeBranchFactory
            .create(
                branch,
                dest.sha1,
                ps.getRefName(),
                strategy.getMergeStrategy(),
                fastForward.getFastForwardMode(),
                message)
            .call()
            .getName();
    dest.add(ps.getId());
  }

  /* A Resolver which ensures that changes are eligible to merge before
   * resolving them.  Once resolved, changes are ordered to minimize the
   * amount of merge commits required to merge them.
   */
  protected class Resolver {
    protected class ParentsNotOnBranchException extends Exception {
      private static final long serialVersionUID = 1L;

      protected ParentsNotOnBranchException(PatchSetArgument psarg) {
        super(
            "No Parent of "
                + psarg.patchSet
                + " is on its destination branch("
                + psarg.change.getDest()
                + ")");
      }
    }

    protected class Destination {
      List<PatchSetArgument> remaining = new ArrayList<>();
      Set<ObjectId> sources = new HashSet<>();

      Destination(Branch.NameKey branch) throws IOException, NoSuchRefException {
        sources.add(getTip(branch));
      }
    }

    protected Map<Branch.NameKey, Destination> destinationsByBranches = new HashMap<>();
    protected List<PatchSetArgument> resolved = new ArrayList<>();

    protected Resolver(Iterable<PatchSetArgument> psargs)
        throws Exception, IOException, OrmException, NoSuchRefException,
            RepositoryNotFoundException {
      add(psargs);
      while (resolve()) {}
      for (Destination dest : destinationsByBranches.values()) {
        if (!dest.remaining.isEmpty()) {
          throw new ParentsNotOnBranchException(dest.remaining.get(0));
        }
      }
      Collections.reverse(resolved); // Reduces merges
    }

    protected boolean resolve()
        throws IOException, OrmException, NoSuchRefException, RepositoryNotFoundException {
      boolean found = false;
      for (Destination dest : destinationsByBranches.values()) {
        // If more dependencies are destined for the same branch than not,
        // then resolving a branch as much as possible will reduce the
        // total iterations required.
        while (resolve(dest)) {
          found = true;
        }
      }
      return found;
    }

    protected boolean resolve(Destination dest)
        throws IOException, OrmException, NoSuchRefException, RepositoryNotFoundException {
      boolean found = false;
      for (PatchSetArgument psarg : dest.remaining) {
        if (isParentMergedInto(psarg, dest.sources)) {
          found = true;
          resolved.add(psarg);
          dest.sources.add(ObjectId.fromString(psarg.patchSet.getRevision().get()));
        }
      }
      dest.remaining.removeAll(resolved);
      return found;
    }

    protected void add(Iterable<PatchSetArgument> psargs) throws IOException, NoSuchRefException {
      for (PatchSetArgument psarg : psargs) {
        add(psarg);
      }
    }

    protected void add(PatchSetArgument psarg) throws IOException, NoSuchRefException {
      Branch.NameKey branch = psarg.change.getDest();
      Destination dest = getDestination(branch);
      dest.remaining.add(psarg);
    }

    protected Destination getDestination(Branch.NameKey b) throws IOException, NoSuchRefException {
      Destination dest = destinationsByBranches.get(b);
      if (dest == null) {
        dest = new Destination(b);
        destinationsByBranches.put(b, dest);
      }
      return dest;
    }
  }

  protected List<ObjectId> getParents(PatchSetArgument psarg) throws IOException {
    PatchSet.Id id = psarg.patchSet.getId();
    List<ObjectId> parents = parentsByPsarg.get(id);
    if (parents == null) {
      parents = loadParents(psarg);
      parentsByPsarg.put(id, parents);
    }
    return parents;
  }

  protected List<ObjectId> loadParents(PatchSetArgument psarg) throws IOException {
    try (Repository repo = repoManager.openRepository(psarg.change.getProject());
        RevWalk revWalk = new RevWalk(repo)) {
      List<ObjectId> parents = new ArrayList<>();
      ObjectId id = ObjectId.fromString(psarg.patchSet.getRevision().get());
      RevCommit c = revWalk.parseCommit(id);
      for (RevCommit parent : c.getParents()) {
        parents.add(parent);
      }
      return parents;
    }
  }
}
