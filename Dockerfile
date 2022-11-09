FROM maven:3.8.6-eclipse-temurin-11 as builder
WORKDIR /home/build
COPY . .
RUN ["mvn", "clean", "package", "-DskipTests"]

FROM eclipse-temurin:11-jre-alpine@sha256:dc28ac69ce6118cb9a4da85de8de7592d3a006af971cf398f1280590d30e82bc as final
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

COPY --from=builder /home/build/docker-startup.sh .
COPY --from=builder /home/build/target/*.yaml .
COPY --from=builder /home/build/target/pay-*-allinone.jar .

ENTRYPOINT ["tini", "-e", "143", "--"]
CMD ["bash", "./docker-startup.sh"]
