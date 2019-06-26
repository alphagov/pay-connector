#!/usr/bin/env bash

set -eu
RUN_MIGRATION=${RUN_MIGRATION:-false}
RUN_APP=${RUN_APP:-true}

if [ -n "${CERTS_PATH:-}" ]; then
  truststore_pass=changeit
  if keytool -cacerts -list -storepass "$truststore_pass" -alias 'custom0' &> /dev/null; then
    echo "$0: Default Java truststore already contains a 'custom0' certificate. Skipping addition of certs from $CERTS_PATH" >&2
    break
  fi
  i=0
  for cert in "$CERTS_PATH"/*; do
    [ -f "$cert" ] || continue
    echo "Adding $cert to default truststore"
    keytool -importcert -noprompt -cacerts -storepass "$truststore_pass" -file "$cert" -alias custom$((i++))
  done
fi

java $JAVA_OPTS -jar *-allinone.jar waitOnDependencies *.yaml

if [ "$RUN_MIGRATION" == "true" ]; then
  java $JAVA_OPTS -jar *-allinone.jar db migrate *.yaml
fi

if [ "$RUN_APP" == "true" ]; then
  exec java $JAVA_OPTS -jar *-allinone.jar server *.yaml
fi
