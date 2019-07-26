#!/bin/bash

set -e

cd "$(dirname "$0")"

mvn -DskipTests clean verify
docker build -t govukpay/connector:local .
