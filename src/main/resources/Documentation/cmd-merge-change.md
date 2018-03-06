@PLUGIN@ merge-change
=====================

NAME
----
@PLUGIN@ merge-change - Merge changes to a batch.

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ merge-change <CHANGE,PATCHSET> ...
 [--strategy {ours|theirs|simple[-two-way-in-core]|resolve}]
 [--message <message>] [--ff | --no-ff | --ff-only] [--close]
```

DESCRIPTION
-----------
Merges the change(s) to the batch branch in the project and prints
the json with the new commit-id of the propsed branch update.

ACCESS
------
Caller must have read permission on a change to merge it to a batch.

SCRIPTING
---------
This command is intended to be used in scripts.

OPTIONS
-------

--strategy

	Use the given merge strategy. Usable strategies are "ours", "theirs",
	"simple", "simple-two-way-in-core", and "resolve".

--message

-m

	Commit message to use when merging commit <COMMIT|REF>.

--ff

	Fast forward update if possible.

--no-ff

	Create a merge commit even for a fast forward merge.

--ff-only

	Abort unless the merge is a fast forward or branch is
	already up-to-date.

--close

	Close the batch on successfull merge.  Closing the batch
	will persist it.


Notes:

--ff, --no-ff, and --ff-only are mutually exclusive options and
--ff is assumed by default.

The default merge strategy is based on the project config for the
destination ref.  If the project config is set to "use content merge",
then it will be "resolve", else it will be "simple-two-way-in-core".

EXAMPLES
--------

Merge a change to a batch:

```
$ ssh -p 29418 review.example.com batch merge 123,3 --close
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
      }
    ]
  }
```
