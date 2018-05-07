#!/bin/bash

CUR_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
source "$CUR_DIR/lib_batch_helpers.sh"
source "$CUR_DIR/lib_batch_json.sh"
source "$CUR_DIR/lib_result.sh"

gcurl() { # data endpoint
    local method=$1; shift
    local end=$1; shift
    curl -X "$method" --user "$USERPASS" $@ "$ENDPOINT$end"
}

get() { # additional_endpoint options
    local end=$1; shift
    gcurl GET "$end" "$@"
}

delete() { # additional_endpoint options
    local end=$1; shift
    gcurl DELETE "$end" "$@"
}

post() { # additional_endpoint data options
    local end=$1; shift
    local data=$1; shift
    gcurl POST "$end" "--data $data" "$@"
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

SILENT_OUTPUT=(--silent '--output /dev/null' '--write-out %{http_code}')
GITURL=ssh://$SERVER:29418/$PROJECT
git ls-remote --heads "$GITURL" >/dev/null || usage "invalid project/server"

REPO_DIR=$(mktemp -d)
trap cleanup EXIT
q git init "$REPO_DIR"

GIT_DIR="$REPO_DIR/.git"
HOOK_DIR="$GIT_DIR/hooks"
FILE_A="$REPO_DIR/fileA"
FILE_B="$REPO_DIR/fileB"

RESULT=0

setupGroup "endpoint" "RestAPI - Endpoint Check" # -------------
# Test the endpoint to make sure it is there, should return 302 "found"
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/302
STATUS="$(get "" ${SILENT_OUTPUT[*]})"
result_out "$GROUP" "302" "$STATUS"

setupGroup "merge-change" "RestAPI - Merge Change" # -------------

# CREATE BATCH - Merge Change
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/200
ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit

postdata='{"batchInput":{"message":"commitmessage","close":true},"mergeChangeInput":{"patchSets":["'$ch1',1"]}}'
STATUS="$(post "batches/" "$postdata" ${SILENT_OUTPUT[*]})"
result_out "$GROUP merge change" "200" "$STATUS"

# CREATE BATCH - Merge Multiple Changes
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/200
ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
ch2=$(create_change "$REF_BRANCH" "$FILE_B") || exit

postdata='{"batchInput":{"message":"commitmessage","close":true},"mergeChangeInput":{"patchSets":["'$ch1',1","'$ch2',1"]}}'
STATUS="$(post "batches/" "$postdata" ${SILENT_OUTPUT[*]})"
result_out "$GROUP merge changes" "200" "$STATUS"

setupGroup "delete" "RestAPI - Batch Delete" # -------------

# DELETE BATCH - Valid Batch ID Provided
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/200
ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
postdata='{"batchInput":{"message":"commitmessage","close":true},"mergeChangeInput":{"patchSets":["'$ch1',1"]}}'
bjson=$(post "batches/" "$postdata" ${SILENT_OUTPUT[0]})
id=$(b_id)

STATUS="$(delete "batches/$id" ${SILENT_OUTPUT[*]})"
result_out "$GROUP valid batch id" "200" "$STATUS"

# DELETE BATCH - Invalid Batch ID Provided
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400
ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
postdata='{"batchInput":{"message":"commitmessage","close":true},"mergeChangeInput":{"patchSets":["'$ch1',1"]}}'
STATUS="$(post "batches/" "$postdata" ${SILENT_OUTPUT[*]})"
id="1234567890"

STATUS="$(delete "batches/$id" ${SILENT_OUTPUT[*]})"
result_out "$GROUP invalid batch id" "400" "$STATUS"

# DELETE BATCH - Valid rest endpoint but not for DELETE
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/405
STATUS="$(delete "batches" ${SILENT_OUTPUT[*]})"
result_out "$GROUP invalid endpoint" "405" "$STATUS"

# DELETE BATCH - No Batch ID Provided
# https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/400
STATUS="$(delete "batches/" ${SILENT_OUTPUT[*]})"
result_out "$GROUP no batch id" "400" "$STATUS"

exit $RESULT