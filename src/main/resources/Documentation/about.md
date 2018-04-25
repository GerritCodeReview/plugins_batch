The @PLUGIN@ plugin provides a mechanism for building and previewing
sets of proposed updates to multiple projects/refs that should be
applied in a batch. These updates are built with Gerrit changes.

While a large focus of Gerrit changes is reviewing, the focus of
batch updates tend to be verification (by CI systems). Batch
updates are not reviewable in the Gerrit UI, but they are
downloadable as git refs. The @PLUGIN@ update service provides the
tools to build these refs by merging changes to temporary "snapshot"
refs, which can then be tested. The intent is to make the same exact
(same git SHA1s) updates testable across potentially many machines.

Creating Batches
----------------
A simple use case for a @PLUGIN@ update might look like this:
a CI systems wants to verify a build for changes 123 (patchset 3),
and 456 (patchset 7) at the same time. These changes are destined
for projectA/branchX, and projectB/branchY respectively. The CI
system (jenkins gerrit user) may start by opening a batch,
merging changes to it, and closing the batch, all in one simple
command like this:

<a name="batchexample"></a>
```
$ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ merge-change 123,3 456,7 --close
  {
    "destinations": [
        {
            "changes": [
                {
                    "number": 123,
                    "patch_set": 3
                }
            ],
            "download_ref": "refs/batch/users/jenkins/0644a132-5b79-4c88-bf22-9364a1d02deb/refs/heads/branchX",
            "project": "projectA",
            "ref": "refs/heads/branchX",
            "sha1": "00de3cf878b8bd51fa56aa9a8d5e8631ae71ad60"
        },
        {
            "changes": [
                {
                    "number": 456,
                    "patch_set": 7
                }
            ],
            "download_ref": "refs/batch/users/jenkins/0644a132-5b79-4c88-bf22-9364a1d02deb/refs/heads/branchY",
            "project": "projectB",
            "ref": "refs/heads/branchY",
            "sha1": "bd26d343b99c25a0704d0ffe5c431900b1cf5c89"
        }
    ],
    "id": "0644a132-5b79-4c88-bf22-9364a1d02deb",
    "last_modified": "July 22, 2014 10:43:28 AM",
    "owner": {
        "id": 1000000
    },
    "state": "CLOSED"
  }
```

Downloading Batches
-------------------
The CI system may then parse this json to get the refs to download the
batch updates from, download and test the batches.

Download Ref Location
---------------------
Download refs have two possible locations. By default the username will be
checked and if present it will be used, and the ref will be stored under the
refs/batch/users/* namespace. In the case that the username is not present
the account id will be used instead, and the ref will be stored under the
refs/batch/accounts/* namespace.

Username present:
`refs/batch/users/{username}/...`

Username not present:
`refs/batch/accounts/{account id}/...`

The format above may be counted on and should be used to set read access
permissons on. The format of the download_ref after the "..."s is internal
and should not be counted on to be stable, use the download_ref field to
access the batch data instead of guessing at the format of this ref.

Batch Storage
-------------
In order to maintain state about which changes are in a batch, and where the
download refs are stored for each batch, the batch data is stored as json on
the special refs/meta/batch/<batch_id> ref in the All-Projects project. This
is internal meta data to the batch plugin and these refs should not be
accessed or altered by users directly.
