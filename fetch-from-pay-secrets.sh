#!/usr/bin/env bash

set -eu

PREFIX=PAY_SECRETS_DECRYPT__

for i in $(env | grep "^${PREFIX}")
do
    key=${i%=*}
    target_key=${key/$PREFIX/}

    secret=$(
        BUCKET="${PAY_SECRETS_BUCKET}" \
        OBJECT="${ECS_SERVICE}"/"${target_key}" \
        ./get_bucket_contents.sh | gpg --quiet -d --batch --yes --passphrase "${!key}")

    unset key
    export ${target_key}="${secret}"
done

if [[ $# -eq 0 ]]
then
    exec ./docker-startup.sh
else
    exec "$@"
fi
