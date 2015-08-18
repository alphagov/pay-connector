#!/usr/bin/env bash
PORT=8080 ADMIN_PORT=8081 DB_USER=postgres DB_PASSWORD=mysecretpassword DB_URL="jdbc:postgresql://192.168.59.103:5432/"  mvn exec:java
