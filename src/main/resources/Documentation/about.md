The @PLUGIN@ plugin provides a mechanism for building and previewing
sets of proposed updates to multiple projects/branches/refs that
should be applied in a batch.  These updates are built with Gerrit
changes.

While a large focus of Gerrit changes is reviewing, the focus of
batch updates tend to be verification (by CI systems).  Batch
updates are not reviewable in the Gerrit UI, but they are
downloadable as git refs.  The @PLUGIN@ update service provides the
tools to build these refs by merging changes to temporary
"snapshot" refs, which can then be tested extensively, and finally
"submitted" as a "unit" if desired.  The intent is to make the
same exact (same git SHA1s) updates testable across many machines
and to apply those exact SHA1s to the final destination refs on
batch submittal.

A simple use case for a @PLUGIN@ update might look like this:
a CI systems wants to verify a build for changes 123 (patchset 3),
and 456 (patchset 7) at the same time.  These changes are destined
for projectA/branchX, and projectB/branchY respectively.  The CI
system (jenkins gerrit user) may start by opening a batch,
merging changes to it, and closing the batch, all in one simple
command like this:

```
$ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ merge-change 123,3 456,7 --close
  {
    "id": "0644a132-5b79-4c88-bf22-9364a1d02deb",
    "owner": {
      "id": 1000000
    },
    "state": "CLOSED",
    "destinations": [
      {
        "project": "projectA",
        "ref": "refs/heads/branchX",
        "sha1": "00de3cf878b8bd51fa56aa9a8d5e8631ae71ad60",
        "download_ref": "refs/batch/users/jenkins/0644a132-5b79-4c88-bf22-9364a1d02deb/refs/heads/branchX",
        "changes": [
          {
            "number": 123,
            "patch_set": 3
          }
        ]
      },
      {
        "project": "projectB",
        "ref": "refs/heads/branchY",
        "sha1": "bd26d343b99c25a0704d0ffe5c431900b1cf5c89",
        "download_ref": "refs/batch/users/jenkins/0644a132-5b79-4c88-bf22-9364a1d02deb/refs/heads/branchY",
        "changes": [
          {
            "number": 456,
            "patch_set": 7
          }
        ]
      }
    ]
  }
```

The CI system may then parse this json to get the refs to
download the batch updates from, download and test the batches,
and finally, on success, submit the batch (using the batch id
that it also parsed from the json) like this:

```
$ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ submit 0644a132-5b79-4c88-bf22-9364a1d02deb
```

This will then predictably apply the exact commits in the "sha1"
entries to the respective destinations.  This allows CI systems to
test changes as if they were already merged on the destination
branches instead of testing them "as is", which might otherwise mean
testing changes which are outdated with respect to the destination
branches.
