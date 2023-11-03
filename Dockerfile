FROM eclipse-temurin:11-jre-alpine@sha256:c033de8d3135b954d230eed6b9bd14dc3dbf32550e448705222f1c907ceebe08

RUN ["apk", "--no-cache", "upgrade"]

ARG DNS_TTL=15

# Default to UTF-8 file.encoding
ENV LANG C.UTF-8

RUN echo networkaddress.cache.ttl=$DNS_TTL >> "$JAVA_HOME/conf/security/java.security"

# Add RDS CA certificates to the default truststore
RUN wget -qO - https://s3.amazonaws.com/rds-downloads/rds-ca-2019-root.pem       | keytool -importcert -noprompt -cacerts -storepass changeit -alias rds-ca-2019-root \
 && wget -qO - https://s3.amazonaws.com/rds-downloads/rds-combined-ca-bundle.pem | keytool -importcert -noprompt -cacerts -storepass changeit -alias rds-combined-ca-bundle \
 && wget -qO - https://truststore.pki.rds.amazonaws.com/eu-west-1/eu-west-1-bundle.pem | keytool -importcert -noprompt -cacerts -storepass changeit -alias rds-eu-west-1-bundle \
 && wget -qO - https://truststore.pki.rds.amazonaws.com/eu-central-1/eu-central-1-bundle.pem | keytool -importcert -noprompt -cacerts -storepass changeit -alias rds-eu-central-1

RUN ["apk", "add", "--no-cache", "bash", "tini"]

ENV PORT 8080
ENV ADMIN_PORT 8081

EXPOSE 8080
EXPOSE 8081

WORKDIR /app

ADD docker-startup.sh .
ADD target/*.yaml .
ADD target/pay-*-allinone.jar .

ENTRYPOINT ["tini", "-e", "143", "--"]

CMD ["bash", "./docker-startup.sh"]
