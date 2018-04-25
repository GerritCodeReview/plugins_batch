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

: Include addtional information for every batch. ([example batch info](about.md#batchexample))

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
