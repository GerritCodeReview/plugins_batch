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

Creating Batches
----------------

A simple use case for a @PLUGIN@ update might look like this:
a CI systems wants to verify a build for changes 123 (patchset 3),
and 456 (patchset 7) at the same time.  These changes are destined
for projectA/branchX, and projectB/branchY respectively.  The CI
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

The CI system may then parse this json to get the refs to
download the batch updates from, download and test the batches.


Download Ref Location
---------------------
The ref location has two possible locations. By default the username will be
checked and if present it will be used, along with storing the ref under "users".
In the case that the username is not present the account id will be used instead,
and the ref will be stored under "accounts".

Username present:
`refs/batch/users/{username}/`
Username not present:
`refs/batch/accounts/{account id}/`


Batch Storage
-------------

In order to maintain state about which changes are in a batch, and where the
download refs are stored for each batch, the batch data is stored as json on
the special refs/meta/batch/<batch_id> ref in the All-Projects project.

Batch Cleanup
-------------

Batches are temporary proposed updates.  They are meant to be
created, tested, and then submitted to their destinations if
they pass, or deleted if they fail.  Since the output of good
batches will likely persist on destination branches, and
the results of bad batches are not typically desirable to keep
around, the batch service has a background *cleaner* task
which finds expired batches and deletes them automatically.
This cleaning helps to ensure that resources are released when
they are no longer needed.

The cleaner task runs by default daily, and batches are expired
by default after 3 days from their last modification.  It is
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
expired (they must be deleted manually).  The default maxAge is
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

: If a unit suffix is not specified, minutes is assumed.  The
default interval is 1 day.

*`cleaner.startDelay`*

: One time delay to wait after plugin load before starting
the periodic cleaner.  The following suffixes are supported
to define the time unit for the delay:

    * m, min, minute, minutes (default suffix)
    * h, hr, hour, hours
    * d, day, days
    * w, week, weeks (1 week is treated as 7 days)
    * mon, month, months (1 month is treated as 30 days)
    * y, year, years (1 year is treated as 365 days)

: If a unit suffix is not specified, minutes is assumed.  The
default startDelay is 1 minute.
