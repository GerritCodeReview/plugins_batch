#!/bin/bash

CUR_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

"$CUR_DIR"/test_batch_ssh.sh "$@" &&
  "$CUR_DIR"/test_batch_rest.sh "$@"