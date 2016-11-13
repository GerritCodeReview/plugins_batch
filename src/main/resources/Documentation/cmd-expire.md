@PLUGIN@ expire
===============

NAME
----
@PLUGIN@ expire - Perform expire operations on a batch.

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ expire <BATCH-ID>
        [--touch]
```

DESCRIPTION
-----------
Perform operations on a batch regarding expiration time

OPTIONS
-----------
**\-\-touch**

: Touch the batch file to reset the expiration time.

ACCESS
------
Only the user that created the batch may modify the batch object.

SCRIPTING
---------
This command is intended to be used in scripts.

EXAMPLES
--------

Touch a batch:

```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ expire 0644a132-5b79-4c88-bf22-9364a1d02deb --touch
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