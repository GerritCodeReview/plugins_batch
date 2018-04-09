#!/bin/bash

CUR_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
source "$CUR_DIR/lib_batch_helpers.sh"
source "$CUR_DIR/lib_batch_json.sh"
source "$CUR_DIR/lib_result.sh"

gcurl() { # method addtional_endpoint
    local method=$1; shift
    curl -X "$method" --user "$USERPASS" --silent --output /dev/null --write-out %{http_code} "$ENDPOINT""$@"
}

get() { # addtional_endpoint
    gcurl GET "$@"
}

delete() { # addtional_endpoint
    gcurl DELETE "$@"
}

usage() { # [error_message]
    local prog=$(basename "$0")

    cat <<-EOF
Usage: $prog [-s|--server <server>] [-p|--project <project>]
             [-r|--ref <ref branch>] [-g|--plugin <plugin>] [-h|--help]

       -h|--help                usage/help
       -g|--plugin <plugin>     plugin to use for the test (default: batch)
       -s|--server <server>     server to use for the test (default: localhost)
       -p|--project <project>   git project to use (default: project0)
       -e|--endpoint <endpoint> url end point for the restapi (default: http://localhost:8080/plugins/batch/)
       -r|--ref <ref branch>    reference branch used to create branches (default: master)
EOF

    [ -n "$1" ] && echo -e '\n'"ERROR: $1"
    exit 1
}

parseArgs() {
    ENDPOINT="http://localhost:8080/a/plugins/batch/"
    PLUGIN="batch"
    SERVER="localhost"
    PROJECT="tools/test/project0"
    REF_BRANCH="master"
    USERPASS="admin:admin"
    while (( "$#" )); do
        case "$1" in
            --endpoint|-e) shift; ENDPOINT=$1 ;;
            --plugin|-g)   shift; PLUGIN=$1 ;;
            --server|-s)   shift; SERVER=$1 ;;
            --project|-p)  shift; PROJECT=$1 ;;
            --ref|-r)      shift; REF_BRANCH=$1 ;;
            --userpass|-u) shift; USERPASS=$1 ;;
            --help|-h)    usage ;;
            --verbose|-v) VERBOSE=$1 ;;
            *)            usage "invalid argument '$1'" ;;
        esac
        shift
    done

    [ -n "$ENDPOINT" ]     || usage "server not set"
    [ -n "$SERVER" ]     || usage "server not set"
    [ -n "$PROJECT" ]    || usage "project not set"
    [ -n "$REF_BRANCH" ] || usage "ref branch not set"
}

# all this is temporary until we have batch creation
# available through the rest api
parseArgs "$@"

GITURL=ssh://$SERVER:29418/$PROJECT
git ls-remote --heads "$GITURL" >/dev/null || usage "invalid project/server"

REPO_DIR=$(mktemp -d)
trap cleanup EXIT
q git init "$REPO_DIR"

GIT_DIR="$REPO_DIR/.git"
HOOK_DIR="$GIT_DIR/hooks"
FILE_A="$REPO_DIR/fileA"

RESULT=0

setupGroup "endpoint" "RestAPI - Endpoint Check" # -------------
# Test the endpoint to make sure it is there, should return 302 "found"
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/302
STATUS="$(get)"
result_out "$GROUP" "$STATUS" "302"


setupGroup "delete" "RestAPI - Batch Delete" # -------------

# DELETE BATCH - Valid Batch ID Provided
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/200
ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
bjson=$(batchssh merge-change --close "$ch1",1)
id=$(b_id)

STATUS="$(delete "batches/$id")"
result_out "$GROUP valid batch id" "$STATUS" "200"

# DELETE BATCH - Invalid Batch ID Provided
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400
ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
bjson=$(batchssh merge-change --close "$ch1",1)
id="1234567890"

STATUS="$(delete "batches/$id")"
result_out "$GROUP invalid batch id" "$STATUS" "400"

# DELETE BATCH - No Batch ID Provided
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400
STATUS="$(delete "batches/")"
result_out "$GROUP no batch id" "$STATUS" "400"


exit $RESULT