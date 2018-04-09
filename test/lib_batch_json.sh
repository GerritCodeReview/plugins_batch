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