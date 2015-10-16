#!/usr/bin/env bash
export DB_URL=jdbc:postgresql://dockerhost:9432/connector
export DB_USER=connector
export DB_PASSWORD=mysecretpassword
export CARD_DETAILS_URL="http://frontend:9000/charge/{chargeId}?chargeTokenId={chargeTokenId}"
export GDS_CONNECTOR_WORLDPAY_URL=http://localhost:3000/stub/worldpay
export GDS_CONNECTOR_WORLDPAY_USER=MERCHANTCODE
export GDS_CONNECTOR_WORLDPAY_PASSWORD=PASSWORD
export GDS_CONNECTOR_SMARTPAY_URL=https://pal-test.barclaycardsmartpay.com/pal/servlet/soap/Payment
export GDS_CONNECTOR_SMARTPAY_USER=USERNAME
export GDS_CONNECTOR_SMARTPAY_PASSWORD=5FuDMdBqv>wdUpi@#bh6JV+8w
export GDS_CONNECTOR_SMARTPAY_NOTIFICATION_USER=unused-in-tests
export GDS_CONNECTOR_SMARTPAY_NOTIFICATION_PASSWORD=unused-in-tests
export PORT=9000
export ADMIN_PORT=9001
java -jar target/*-allinone.jar db migrate target/*.yaml
mvn exec:java
