#!/bin/bash

set -e

cd "$(dirname "$0")"

mvn -DskipITs clean verify
docker build -t govukpay/connector:local .
