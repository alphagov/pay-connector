#!/bin/bash
mvn -DskipTests package && docker build -t govukpay/connector:local .