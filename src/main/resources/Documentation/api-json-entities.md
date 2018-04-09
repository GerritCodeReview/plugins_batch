@PLUGIN@ - API JSON Entities
==============================

### JSON Info Entities

#### BatchInfo

The `BatchInfo` entity contains information about a batch.

* _id_: The ID of the Batch being deleted.
* _version_: The version of the Batch.
* _owner\id_: The ID of the owner of the Batch.
* _state_: Current state of this Batch.
* _destinations_: List of [BatchDestinationInfo](#batchdestinationinfo) entities.
* _last\_modified_: Timestamp of the last time this batch was modified.


#### BatchDestinationInfo

The `BatchDestinationInfo` entity contains information about a destination.

* _project_: Destination Project.
* _ref_: Destination Ref.
* _sha1_: Destination SHA.
* _download\_ref_: Download Ref for this Batch.
* _changes_: List of [BatchChangeInfo](#batchchangeinfo) entities.


#### BatchChangeInfo

The `BatchChangeInfo` entity contains information about a change.

* _number_: Change number of a change in the batch.
* _patch\_set_: PatchSet number of a change in the batch.