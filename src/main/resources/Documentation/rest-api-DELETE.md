@PLUGIN@ - DELETE REST API
==============================

This page describes the REST endpoints that are added by the @PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

<a name="endpoints"></a>@PLUGIN@ - Endpoints
-----------------------------------

### Delete Batch
_DELETE /plugins/@PLUGIN@/batches/\{batch id\}_

Deletes a batch based on the ID passed over.

#### Request

```
  DELETE /plugins/@PLUGIN@/batches/12345 HTTP/1.0
```

As response a [BatchInfo](api-json-entities.md#batchinfo) entity is returned
that describes the batch.

#### Response

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
    "state": "DELETED",
    "destinations": [
      {
        "project": "delete-test",
        "ref": "refs/meta/config",
        "sha1": "d4a2a4ef5b712389fa74b79f4355f594bb9d35ae",
        "download_ref": "refs/batch/users/admin/b4a44f0e-cd3d-492d-8e0c-acb52e9732a1/refs/meta/config",
        "changes": [
            {
              "number": 22,
              "patch_set": 1
            },
            {
              "number": 21,
              "patch_set": 1
            },
            {
              "number": 402,
              "patch_set": 1
            }
        ]
      }
    ],
    "last_modified": "July 22, 2015 11:59:45 AM"
  }
```