#!/usr/bin/env bash

set -eu

unset AWS_ACCESS_KEY_ID
unset AWS_SECRET_ACCESS_KEY
unset AWS_SESSION_TOKEN

function get_value_for_key {
    key="$1"
    json="$2"

    regex='"'"${key}"'"\s*:\s*"([^"]*)'

    if [[ ${json} =~ ${regex} ]]
    then
	echo ${BASH_REMATCH[1]}
    fi
}

response=$(curl 169.254.170.2$AWS_CONTAINER_CREDENTIALS_RELATIVE_URI)

export AWS_ACCESS_KEY_ID=$(get_value_for_key 'AccessKeyId' $response)
export AWS_SECRET_ACCESS_KEY=$(get_value_for_key 'SecretAccessKey' $response)
export AWS_SESSION_TOKEN=$(get_value_for_key 'Token' $response)

exec "$@"
