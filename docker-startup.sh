#!/usr/bin/env bash

if [ "${ENABLE_NEWRELIC}" == "yes" ]; then
  NEWRELIC_JVM_FLAG="-javaagent:/app/newrelic/newrelic.jar"
fi

# It'd be nice if Java could digest the standard HTTP_PROXY and
# HTTPS_PROXY environment variables, but no. Rather than do too much
# munging of variables in this script, I expect whatever is handling
# the containers to set the mulitude of variables required.

if [ -n "${HTTP_PROXY_HOST}" && -n "${HTTP_PROXY_PORT}" ] ; then
    HTTP_PROXY_SETTINGS="-Dhttp.proxyHost=${HTTP_PROXY_HOST} -Dhttp.proxyPort=${HTTP_PROXY_PORT}"
    HTTPS_PROXY_SETTINGS="-Dhttps.proxyHost=${HTTPS_PROXY_HOST} -Dhttps.proxyPort=${HTTPS_PROXY_PORT}"

    if [ -n "${HTTP_NON_PROXY_HOSTS}" ] ; then
        NON_PROXY_SETTING="-Dhttp.nonProxyHosts=${HTTP_NON_PROXY_HOSTS}"
    fi

    PROXY_SETTINGS="${HTTP_PROXY_SETTINGS} ${HTTPS_PROXY_SETTINGS} ${NON_PROXY_SETTING}"
fi

java \
  ${NEWRELIC_JVM_FLAG} \
  ${PROXY_SETTINGS} \
  -jar *-allinone.jar server *.yaml
