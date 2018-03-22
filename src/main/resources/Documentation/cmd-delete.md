@PLUGIN@ delete
===============

NAME
----
@PLUGIN@ delete - Delete a batch.

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ delete <BATCH-ID>
```

DESCRIPTION
-----------
Delete the batch and any refs it points to.

ACCESS
------
Any user who has configured an SSH key, and is a member
of the privileged 'Administrators' group.

SCRIPTING
---------
This command is intended to be used in scripts.

EXAMPLES
--------

Delete a batch:

```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ delete 0644a132-5b79-4c88-bf22-9364a1d02deb
```
