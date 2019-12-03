#!/bin/bash

set -eu

export POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-mysecretpassword}
export POSTGRES_USER=${POSTGRES_USER:-postgres}
export POSTGRES_DB=${POTGRES_DB:-postgres}

docker run --name posttest -e POSTGRES_PASSWORD -e POSTGRES_USER -e POSTGRES_DB -d --rm -p 5432:5432 govukpay/postgres:9.6.12

