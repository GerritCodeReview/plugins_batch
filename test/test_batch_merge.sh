#!/bin/bash

# ---- JSON PARSING ----
json_val_by() { # json index|'key' > value
    echo "$1" | python -c "import json,sys;print json.load(sys.stdin)[$2]"
}
json_jval_by() { # json index|'key' > json_value
    echo "$1" |\
    python -c "import json,sys;print json.dumps(json.load(sys.stdin)[$2])"
}
json_val_by_key() { json_val_by "$1" "'$2'" ; }  # json key > value
json_jval_by_key() { json_jval_by "$1" "'$2'" ; }  # json key > json_value

# ---- TEST RESULTS ----
result() { # test [error_message]
    local result=$?
    if [ $result -eq 0 ] ; then
        echo "PASSED - $1 test"
    else
        echo "*** FAILED *** - $1 test"
        RESULT=$result
        [ $# -gt 1 ] && echo "$2"
    fi
}

# output must match expected to pass
result_out() { # test expected output
    local disp=$(echo "Expected Output:" ;\
                 echo "    $2" ;\
                 echo "Actual Output:" ;\
                 echo "    $3")

    [ "$2" = "$3" ]
    result "$1" "$disp"
}

# ---- Low level execution helpers ----
q() { "$@" > /dev/null 2>&1 ; } # cmd [args...]  # quiet a command
gssh() { ssh -p 29418 -x "$SERVER" "$@" 2>&1 ; } # run a gerrit ssh command
batchssh() { # run a batch ssh command
    local out rtn
    out=$(gssh "$PLUGIN" "$@") ; rtn=$? ; echo "$out"
    [ -n "$VERBOSE" ] && echo "$out" >&2
    return $rtn
}
query() { gssh gerrit query "$@" ; }
remote_show() { # remote_ref > sha
    git ls-remote "$GITURL" 2>/dev/null | awk '$2 == "'"$1"'" {print $1}'
}
mygit() { git --work-tree="$REPO_DIR" --git-dir="$GIT_DIR" "$@" ; } # [args...]

# ---- Custom batch getters -----
b_id() { json_val_by_key "$bjson" id ; } # > batch_id
b_state() { json_val_by_key "$bjson" state ; } # > batch_state
b_destination() { # index  # > batch_destination[index]
    json_jval_by "$(json_jval_by_key "$bjson" destinations)" "$1"
}

# ---- Custom batch destination getters -----
d_ref() { json_val_by_key "$1" ref ; } # destination > ref
d_project() { json_val_by_key "$1" project ; } # destination > project
d_sha1() { json_val_by_key "$1" sha1 ; } # destination > sha1
d_download() { json_val_by_key "$1" download_ref ; } # destination > download_ref

# ---- Parsers ----
query_by() { echo "$1" | awk '/^ *'"$2"':/{print $2}' ; } # qchange key > val

get_change_num() { # < gerrit_push_response > changenum
    local url=$(awk '/New Changes:/ { getline; print $2 }')
    echo "${url##*\/}" | tr -d -c '[:digit:]'
}

nn() { # change_num > nn
    local nn=$(($1 % 100))
    [ "$nn" -lt 10 ] && nn=0$nn
    echo "$nn"
}

#----
get_ref_parents() { # ref > p1 p2
    q mygit fetch "$GITURL" "$1" || exit 1
    mygit rev-list -1 FETCH_HEAD --parents | cut -d' ' -f2,3
}

create_change() { # [--dependent] branch file [file_content] > changenum
    local opt_d
    [ "$1" = "--dependent" ] && { opt_d=$1 ; shift ; }
    local branch=$1 tmpfile=$2 content=$3 out rtn
    [ -n "$content" ] || content=$RANDOM

    if [ -z "$opt_d" ] ; then
        out=$(mygit fetch "$GITURL" "$branch" 2>&1) ||\
            cleanup "Failed to fetch $branch: $out"
        out=$(mygit checkout FETCH_HEAD 2>&1) ||\
            cleanup "Failed to checkout $branch: $out"
    fi

    echo -e "$content" > "$tmpfile"

    out=$(mygit add "$tmpfile" 2>&1) || cleanup "Failed to git add: $out"

    out=$(scp -p -P 29418 "$SERVER":hooks/commit-msg $HOOK_DIR 2>&1) || cleanup "Failed to fetch commit_msg hook: $out"

    out=$(mygit commit -m "Add $tmpfile" 2>&1) ||\
        cleanup "Failed to commit change: $out"

    out=$(mygit push "$GITURL" "HEAD:refs/for/$branch" 2>&1) ||\
        cleanup "Failed to push change: $out"
    out=$(echo "$out" | get_change_num) ; rtn=$? ; echo "$out"
    [ -n "$VERBOSE" ] && echo "  change:$out" >&2
    return $rtn
}

setupGroup() { # shortname longname
    GROUP=$1
    echo
    echo  "$2"
    echo "----------------------------------------------"
}

cleanup() { # [error_message]
    rm -rf "$REPO_DIR"

    if [ -n "$1" ] ; then
        echo "$1, unable to perform batch tests" >&2
        exit 1
    fi
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


setupGroup "ls-batches" "List Batches" # -------------

ch1=$(create_change "$REF_BRANCH" "$FILE_A") || exit
bjson=$(batchssh merge-change --close "$ch1",1)
list=$(batchssh ls-batches --include-batch-info)
echo "$list"| grep '"id"'| grep -q "$(b_id)"
result "$GROUP" "$list"
echo "$list"| grep -q '"last_modified'
result "$GROUP last_modified" "listResult: $list"


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
