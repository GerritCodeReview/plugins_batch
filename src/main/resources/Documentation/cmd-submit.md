@PLUGIN@ submit
===============

NAME
----
@PLUGIN@ submit - Submit a batch to its destinations.

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ submit <BATCH-ID>
```

DESCRIPTION
-----------
Submit the batch updates to their destinations, and force merge any
Gerrit changes in the batch.  Once the batch is submitted, it will
be automatically cleaned up.  This will effectively bypass any Gerrit
submit rules for changes and will behave as if the batch SHA1s had
been pushed directly to the destination branches.

ACCESS
------
Caller must have permission to push updates to the destinations
in the batch.

SCRIPTING
---------
This command is intended to be used in scripts.

OPTIONS
-------

--force

	force push the batch updates

EXAMPLES
--------

Submit a batch:

```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ submit 0644a132-5b79-4c88-bf22-9364a1d02deb
```
