@PLUGIN@ - API JSON Entities
==============================


### JSON Input Entities

#### MergeInput

The `MergeInput` entity is the top level entity when merging changes to create a batch.

* `batchInput` _BatchInput_: Batch specific input options. See [BatchInput](#batchinput)
* `mergeChangeInput` _MergeChangeInput_: Merge Change specific input options. See [MergeChangeInput](#mergechangeinput)
* `mergeOptionsInput` _MergeOptionsInput_: Merge input options. See [MergeOptionsInput](#mergeoptionsinput)


#### BatchInput

The `BatchInput` entity contains batch specific input options used when creating a batch.

* `close` _boolean_: Close batch on merge success. _(true|false)_
* `message` _string_: Commit message to use when applying a change.


#### MergeChangeInput

The `MergeChangeInput` entity contains merge change specific input used when creating a batch.

* `patchSets` _string_: Array of PatchSets to merge. _(["12,3","45,6"])_


#### MergeOptionsInput

The `MergeOptionsInput` entity contains merge options used when creating a batch.

* `mergeMode` _string_: Merge mode to use, must be one of the listed. _(ff|noff|ffOnly|cherryPick)_
* `strategy` _string_: Use the given merge strategy, must be one of the listed. _(ours|theirs|simple[-two-way-in-core]|resolve)_


### JSON Info Entities

#### BatchInfo

The `BatchInfo` entity contains information about a batch.

* _id_: The ID of the Batch being deleted.
* _version_: The version of the Batch.
* _owner_: Owner entity. [BatchOwnerInfo](#batchownerinfo)
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


#### BatchOwnerInfo

The `BatchOwnerInfo` entity contains information about the batch owner.

* _id_: The ID of the owner of the Batch.
