# ---- TEST RESULTS ----

result() { # test [error_message]
    local result=$?
    local outcome="FAIL"
    if [ $result -eq 0 ] ; then
        echo "PASSED - $1 test"
        outcome="PASS"
    else
        echo "*** FAILED *** - $1 test"
        RESULT=$result
        [ $# -gt 1 ] && echo "$2"
    fi
    [ -n "$RESULT_CALLBACK" ] &&
        "$RESULT_CALLBACK" "$(basename "$0")" "$1" "$outcome"
}

# $1 - HTTP response 'STATUS'
# $2 - Expected HTTP response
# $3 - Name or description of the test
rest_result() {
    if [ $1 -eq $2 ] ; then
        echo "PASSED - $3 test"
    else
        echo "*** FAILED *** - $3 test"
        RESULT=1
        [ $# -gt 1 ] && echo "Response - $1 did not match the expected response - $2."
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