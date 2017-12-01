#!/bin/sh

function postMetricToGraphite() {
  if [ $# -ne 2 ]; then
      echo "You must supply metric name and metric value"
      exit 1
  fi

  if [ -z "${HOSTED_GRAPHITE_ACCOUNT_ID}" ] ; then
      echo "You must supply an account id for the carbon prefix"
      exit 1
  fi

  if [ "${HOSTED_GRAPHITE_API_KEY}" ]; then
    METRIC_NAME="${HOSTED_GRAPHITE_API_KEY}.$1"
    echo "Sending metric '$METRIC_NAME'"
    echo "$METRIC_NAME $2" | nc "${HOSTED_GRAPHITE_ACCOUNT_ID}.carbon.hostedgraphite.com" 2003
  fi
}