The @PLUGIN@ plugin provides a mechanism for building and previewing
sets of proposed updates to multiple projects/refs that should be
applied in a batch. These updates are built with Gerrit changes.

While a large focus of Gerrit changes is reviewing, the focus of
batch updates tend to be verification (by CI systems). Batch
updates are not reviewable in the Gerrit UI, but they are
downloadable as git refs. The @PLUGIN@ update service provides the
tools to build these refs by merging changes to temporary "snapshot"
refs, which can then be tested and finally "submitted" as a "unit"
if desired. The intent is to make the same exact (same git SHA1s)
updates testable across potentially many machines, and to apply
those exact SHA1s to the final destination refs on batch submittal.

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

Submitting Batches
------------------
As a final step, the CI system may, on success, submit the batch (using the
batch id that it parsed from the json) like this:

```
$ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ submit --force \
  0644a132-5b79-4c88-bf22-9364a1d02deb
```

This will then predictably apply the exact commits in the "sha1"
entries to the respective destinations using a force push approach. This
allows CI systems to test changes as if they were already merged on the
destination branches instead of testing them "as is", which might
otherwise mean testing changes which are outdated with respect to the
destination branches.

Because neither git nor Gerrit supports updating refs atomically across
repositories, the forced push approach has been found to be the most
reliable approach for CI systems to use to ensure that what they tested
gets applied to their branches. Using a fored push strategy requires
that the account submitting batches have FORCE PUSH permissons. To
make this work reliably and to ensure that no history is ever lost, it
is important that batches are only ever built from the current tips, and
that those batches get submitted before any of the tips change. If other
batches or changes are submitted to the batch branches after the batch
was created but before it is submitted using force push, then some
history will likely be lost. The risk of this occurring is a generally
seen as a worthwhile tradeoff to ensure that only what has been tested
ever gets merged into a branch's history. It is advisable to only ever
update branches that will have batches submitted to them by an actor
that can create and submit batches "serially" and be the only actor
updating these branches. This can be achieved by removing SUBMIT
permissions from all accounts and giving a single account, used by a
single process, FORCE PUSH permission to create and submit batches
following the guidelines above.

Batch Storage
-------------
In order to maintain state about which changes are in a batch, and where the
download refs are stored for each batch, the batch data is stored as json on
the special refs/meta/batch/<batch_id> ref in the All-Projects project. This
is internal meta data to the batch plugin and these refs should not be
accessed or altered by users directly.

Batch Cleanup
-------------
Batches are temporary proposed updates. They are meant to be
created, tested, and then submitted to their destinations if
they pass, or deleted if they fail. Since the output of good
batches will likely persist on destination branches, and
the results of bad batches are not typically desirable to keep
around, the batch service has a background *cleaner* task
which finds expired batches and deletes them automatically.
This cleaning helps to ensure that resources are released when
they are no longer needed.

The cleaner task runs by default daily, and batches are expired
by default after 3 days from their last modification. It is
possible to configure expiration times, and the cleaner using
the `All-Projects` `refs/meta/config` `@PLUGIN@.config` file.
The `@PLUGIN@.config` file is a "git-config" style file
and supports the following parameters:

*`cleaner.maxAge`*

: Age after which a batch is considered expired. Values should
use common unit suffixes to express their setting:

    * s, sec, second, seconds (default unit)
    * m, min, minute, minutes
    * h, hr, hour, hours
    * d, day, days
    * w, week, weeks (1 week is treated as 7 days)
    * mon, month, months (1 month is treated as 30 days)
    * y, year, years (1 year is treated as 365 days)

: If a unit suffix is not specified, seconds is assumed. If 0 is
supplied, the maximum age is infinite and items are never
expired (they must be deleted manually). The default maxAge is
3 days.

*`cleaner.interval`*

: Interval for periodic repetition of triggering the batch
cleanups. The interval must be larger than zero. The following
suffixes are supported to define the time unit for the interval:

    * m, min, minute, minutes (default suffix)
    * h, hr, hour, hours
    * d, day, days
    * w, week, weeks (1 week is treated as 7 days)
    * mon, month, months (1 month is treated as 30 days)
    * y, year, years (1 year is treated as 365 days)

: If a unit suffix is not specified, minutes is assumed. The
default interval is 1 day.

*`cleaner.startDelay`*

: One time delay to wait after plugin load before starting
the periodic cleaner. The following suffixes are supported
to define the time unit for the delay:

    * m, min, minute, minutes (default suffix)
    * h, hr, hour, hours
    * d, day, days
    * w, week, weeks (1 week is treated as 7 days)
    * mon, month, months (1 month is treated as 30 days)
    * y, year, years (1 year is treated as 365 days)

: If a unit suffix is not specified, minutes is assumed. The
default startDelay is 1 minute.
