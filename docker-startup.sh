#!/usr/bin/env bash
java -jar *-allinone.jar waitOnDependencies *.yaml && \
  java $JAVA_OPTS -jar *-allinone.jar server *.yaml
