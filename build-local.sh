#!/bin/bash
mvn -DskipTests clean package && docker build -t govukpay/connector:latest-master .
