FROM eclipse-temurin:11-jre-alpine@sha256:64674afdc275bed87f352eb94f9469842313dd2902afe9d7b0fb9508fc75cc37

RUN ["apk", "--no-cache", "upgrade"]

ARG DNS_TTL=15

# Default to UTF-8 file.encoding
ENV LANG C.UTF-8

RUN echo networkaddress.cache.ttl=$DNS_TTL >> "$JAVA_HOME/conf/security/java.security"

# Add RDS CA certificates to the default truststore
RUN wget -qO - https://s3.amazonaws.com/rds-downloads/rds-ca-2019-root.pem       | keytool -importcert -noprompt -cacerts -storepass changeit -alias rds-ca-2019-root \
 && wget -qO - https://s3.amazonaws.com/rds-downloads/rds-combined-ca-bundle.pem | keytool -importcert -noprompt -cacerts -storepass changeit -alias rds-combined-ca-bundle

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
