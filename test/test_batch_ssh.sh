#!/bin/bash

CUR_DIR="$(dirname "$0")"
source "$CUR_DIR/lib_batch_helpers.sh"
source "$CUR_DIR/lib_batch_json.sh"
source "$CUR_DIR/lib_result.sh"

usage() { # [error_message]
    local prog=$(basename "$0")

    cat <<-EOF
Usage: $prog [-s|--server <server>] [-p|--project <project>]
             [-r|--ref <ref branch>] [-g|--plugin <plugin>] [-h|--help]

       -h|--help                usage/help
       -g|--plugin <plugin>     plugin to use for the test (default: batch)
       -s|--server <server>     server to use for the test (default: localhost)
       -p|--project <project>   git project to use (default: project0)
       -r|--ref <ref branch>    reference branch used to create branches (default: master)
EOF

    [ -n "$1" ] && echo -e '\n'"ERROR: $1"
    exit 1
}

parseArgs() {
    PLUGIN="batch"
    SERVER="localhost"
    PROJECT="tools/test/project0"
    REF_BRANCH="master"
    while (( "$#" )); do
        case "$1" in
            --plugin|-g)  shift; PLUGIN=$1 ;;
            --server|-s)  shift; SERVER=$1 ;;
            --project|-p) shift; PROJECT=$1 ;;
            --ref|-r)     shift; REF_BRANCH=$1 ;;
            --help|-h)    usage ;;
            --verbose|-v) VERBOSE=$1 ;;
            *)            usage "invalid argument '$1'" ;;
        esac
        shift
    done

    [ -n "$SERVER" ]     || usage "server not set"
    [ -n "$PROJECT" ]    || usage "project not set"
    [ -n "$REF_BRANCH" ] || usage "ref branch not set"
}

parseArgs "$@"

GITURL=ssh://$SERVER:29418/$PROJECT
git ls-remote --heads "$GITURL" >/dev/null || usage "invalid project/server"
DEST_REF=refs/heads/$REF_BRANCH

REPO_DIR=$(mktemp -d)
trap cleanup EXIT
q git init "$REPO_DIR"

GIT_DIR="$REPO_DIR/.git"
HOOK_DIR="$GIT_DIR/hooks"
FILE_A="$REPO_DIR/fileA"
FILE_B="$REPO_DIR/fileB"

RESULT=0

setupGroup "merge-change" "Merge Change" # -------------

ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
bjson=$(batchssh merge-change --close "$ch1",1)
result "$GROUP" "$bjson"

id=$(b_id)
dest1=$(b_destination 0)
sha1=$(d_sha1 "$dest1")
qchange=$(query "$ch1" --current-patch-set)

result_out "$GROUP project" "$PROJECT" "$(d_project "$dest1")"
result_out "$GROUP ref" "$DEST_REF" "$(d_ref "$dest1")"
result_out "$GROUP sha1" "$(query_by "$qchange" "revision")" "$sha1"
result_out "$GROUP state" "CLOSED" "$(b_state)"
result_out "$GROUP change_state" "NEW" "$(query_by "$qchange" "status")"


setupGroup "delete" "Batch Delete" # -------------

ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
bjson=$(batchssh merge-change --close "$ch1",1)
id=$(b_id)
delete=$(batchssh delete "$id")
result "$GROUP" "$delete"
! delete=$(batchssh delete "$id")
result "$GROUP retry" "$delete"


setupGroup "independent clean" "Independent changes, clean merge" # ------------

ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
ch2=$(create_change "$REF_BRANCH" "$FILE_B") || exit
bjson=$(batchssh merge-change --close "$ch1",1 "$ch2",1)
result "$GROUP" "$bjson"


setupGroup "independent conflict" "Independent changes, merge conflict" # ------

ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
ch2=$(create_change "$REF_BRANCH" "$FILE_A") || exit
bjson=$(batchssh merge-change --close "$ch1",1 "$ch2",1)
echo "$bjson"| grep -q "Couldn't merge change"
result "$GROUP" "$bjson"


setupGroup "dependent changes ff" "Dependent changes, fast forward" # ---------

ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
ch2=$(create_change --dependent "$REF_BRANCH" "$FILE_B") || exit
bjson=$(batchssh merge-change --close "$ch1",1 "$ch2",1)
id=$(b_id)
dest1=$(b_destination 0)
sha1=$(d_sha1 "$dest1")
qchange=$(query "$ch2" --current-patch-set)
result_out "$GROUP sha1" "$(query_by "$qchange" "revision")" "$sha1"
bjson=$(batchssh merge-change --close "$ch2",1 "$ch1",1)
result "$GROUP reverse" "$bjson"
! bjson=$(batchssh merge-change --close "$ch2",1)
result "$GROUP missing" "$bjson"

exit $RESULT