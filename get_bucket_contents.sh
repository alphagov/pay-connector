#!/usr/bin/env bash

set -eu

function hmac_sha256 () {
  key="$1"
  data="$2"
  printf '%b' "$data" | openssl dgst -sha256 -mac HMAC -macopt "$key" | sed 's/^.* //'
}

service=s3
region=eu-west-1

http_method=GET
canonical_url="/${OBJECT}"
canonical_query_string=''
hashed_payload=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

timestamp=$(date -u +"%Y%m%dT%H%M00Z")
date=$(date -u +"%Y%m%d")

host="${BUCKET}.s3.${region}.amazonaws.com"

date_key=$(hmac_sha256 "key:AWS4${AWS_SECRET_ACCESS_KEY}" "${date}")
date_region_key=$(hmac_sha256 "hexkey:${date_key}" "${region}")
date_region_service_key=$(hmac_sha256 "hexkey:${date_region_key}" "${service}")
signing_key=$(hmac_sha256 "hexkey:${date_region_service_key}" "aws4_request")

canonical_headers="host:$host\nx-amz-content-sha256:$hashed_payload\nx-amz-date:$timestamp\nx-amz-security-token:$AWS_SESSION_TOKEN\n"

signed_headers='host;x-amz-content-sha256;x-amz-date;x-amz-security-token'

canonical_request="${http_method}\n${canonical_url}\n${canonical_query_string}\n${canonical_headers}\n${signed_headers}\n${hashed_payload}"

canonical_request_hash=$(printf '%b' "${canonical_request}" | openssl sha256 | sed 's/^.* //')

scope=${date}/${region}/${service}/aws4_request

message="AWS4-HMAC-SHA256\n${timestamp}\n${scope}\n${canonical_request_hash}"

signature=$(hmac_sha256 "hexkey:${signing_key}" "${message}")

curl -S --proxy '' https://"${host}"/${OBJECT} \
    -H "Authorization: AWS4-HMAC-SHA256 \
        Credential=${AWS_ACCESS_KEY_ID}/${scope}, \
        SignedHeaders=${signed_headers}, \
        Signature=${signature}" \
    -H "x-amz-content-sha256: $hashed_payload" \
    -H "x-amz-date: $timestamp" \
    -H "x-amz-security-token: $AWS_SESSION_TOKEN"
