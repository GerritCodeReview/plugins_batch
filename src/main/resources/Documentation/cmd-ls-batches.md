@PLUGIN@ ls-batches
=====================

NAME
----
@PLUGIN@ ls-batches - List batches visible to caller

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ ls-batches
  [--include-last-modified]
  <query>
```

DESCRIPTION
-----------
Displays the list of batches, in JSON.

OPTIONS
-----------
**\-\-include-last-modified**

: Include the time of the last modification for every batch.

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
<a id="expired"/>
*expired:true*

: Batches which have expired ([see batch cleanup](batch.html#cleanup))

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
