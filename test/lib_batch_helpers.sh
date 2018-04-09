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

