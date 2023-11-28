#!/bin/bash

set -e

cd "$(dirname "$0")"

if [ "$(uname -m)" == "arm64" ]; then
  docker build -t governmentdigitalservice/pay-connector:local -f m1/arm64.Dockerfile .
else
  docker build -t governmentdigitalservice/pay-connector:local .
fi
