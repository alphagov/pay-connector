#!/usr/bin/env bash

set -eu
RUN_MIGRATION=${RUN_MIGRATION:-false}
RUN_APP=${RUN_APP:-true}

java -agentlib:hprof=cpu=samples,interval=500,depth=10 -jar *-allinone.jar waitOnDependencies *.yaml

if [ "$RUN_MIGRATION" == "true" ]; then
  java $JAVA_OPTS -jar *-allinone.jar db migrate *.yaml
fi

if [ "$RUN_APP" == "true" ]; then
  java $JAVA_OPTS -jar *-allinone.jar server *.yaml
fi

exit 0
