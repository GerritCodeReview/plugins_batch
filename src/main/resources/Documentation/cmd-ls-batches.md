@PLUGIN@ ls-batches
=====================

NAME
----
@PLUGIN@ ls-batches - List batches visible to caller

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ ls-batches
  [--include-batch-info]
  <query>
```

OPTIONS
-----------
**\-\-include-batch-info**

: Include additional information for every batch. ([example batch info](about.md#batchexample))

DESCRIPTION
-----------
Displays the list of batches, in JSON.

ACCESS
------
Any user who has configured an SSH key, and is a member
of the privileged *Administrators* group.

SCRIPTING
---------
This command is intended to be used in scripts.

QUERIES
-------
Batches can be queried using a syntax that mimicks the change
query syntax.  Operators act as restrictions on the search.  As
more operators are added to the same query string, they further
restrict the returned results.

Operators
---------
<a name="is:expired"></a>
*is:expired*

: Batches which have expired ([see batch cleanup](about.md#cleanup))


EXAMPLES
--------
List visible batches:

```
  ssh -p 29418 review.example.com batch ls-batches
  [
    {
      "id": "c2a24c5a-dbb4-43f9-bec7-d999cc7b3f53"
    },
    ...,
    {
      "id": "42aa58ae-85e9-4e46-abd9-c82391b4222c"
    }
  ]
```

List visible batches with info:
```
  ssh -p 29418 review.example.com batch ls-batches --include-batch-info
  [
    {
      "destinations":[
        {
          "changes":[
            {
              "number":22,
              "patch_set":1
            },
            {
              "number":21,
              "patch_set":1
            },
            {
              "number":402,
              "patch_set":1
            }
          ],
          "download_ref":"refs/batch/users/jenkins/0644a132-5b79-4c88-bf22-9364a1d02deb/refs/heads/branchX",
          "project":"projectA",
          "ref":"refs/heads/branchX",
          "sha1":"00de3cf878b8bd51fa56aa9a8d5e8631ae71ad60"
        },
      ],
      "id":"0644a132-5b79-4c88-bf22-9364a1d02deb",
      "last_modified":"July 22, 2014 10:43:28 AM",
      "owner":{
        "id":1000000
      },
      "state":"CLOSED"
    }
  ]
```
