@PLUGIN@ - POST REST API
==============================

This page describes the REST endpoints that are added by the @PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

<a name="endpoints"></a>@PLUGIN@ - Endpoints
-----------------------------------

### Merge Changes to a Batch
_POST /plugins/@PLUGIN@/batches_

Merges the change(s) to the batch branch in the project and prints out
the json with the id and info of the proposed branch update.

#### Request

Options for this endpoint can be specified in the request body as a
[MergeInput](api-json-entities.md#mergeinput) entity.

```
  POST /plugins/@PLUGIN@/batches HTTP/1.0
  Content-Type: application/json

  {
    "batchInput": {
      "close": true,
      "message": "commit messages are fun"
    },
    "mergeChangeInput": {
      "patchSets": [
        "12,3",
        "45,6"
      ]
    }
  }


  POST /plugins/@PLUGIN@/batches HTTP/1.0
  Content-Type: application/json

  {
    "batchInput": {
      "close": true,
      "message": "commit messages are awesome"
    },
    "mergeChangeInput": {
      "patchSets": [
        "12,3",
        "45,6",
        "78,9",
        "10,11"
      ]
    },
    "merge-options": {
      "merge-mode": "no-ff"
    }
  }
```

#### Response

As response a [BatchInfo](api-json-entities.md#batchinfo) entity is returned that describes the batch that was created.

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json;charset=UTF-8

  )]}'
  {
    "id": "b4a1230e-cd3d-492d-8e0c-acb52e9732a1",
    "version": 0,
    "owner": {
        "id": 1231234
    },
    "state": "CLOSED",
    "destinations": [
      {
        "project": "bacth-test",
        "ref": "refs/meta/config",
        "sha1": "d4a2a4ef5b712389fa74b79f4355f594bb9d35ae",
        "download_ref": "refs/batch/users/admin/b4a44f0e-cd3d-492d-8e0c-acb52e9732a1/refs/meta/config",
        "changes": [
            {
            "number": 12,
            "patch_set": 3
            },
            {
            "number": 45,
            "patch_set": 6
            },
            {
            "number": 78,
            "patch_set": 9
            }
        ]
      }
    ],
    "last_modified": "July 22, 2015 11:59:45 AM"
  }
```