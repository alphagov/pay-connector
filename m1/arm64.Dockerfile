FROM maven:3.9.5-eclipse-temurin-11@sha256:d698c543af44a1a8e4b2b2f9c1cfe3e009f2398cc7125a3d1204c71ad876800f AS builder

WORKDIR /home/build
COPY . .

RUN ["mvn", "clean", "--no-transfer-progress", "package", "-DskipTests"]

FROM eclipse-temurin:11-jre@sha256:f31717a7a4c0d7cc9e50008544d269c90d6894dd70cdcbb28da8060913fcf8ad AS final

ARG DNS_TTL=15

# Default to UTF-8 file.encoding
ENV LANG C.UTF-8

RUN echo networkaddress.cache.ttl=$DNS_TTL >> "$JAVA_HOME/conf/security/java.security"

RUN apt-get update && apt-get upgrade -y && apt-get install -y tini wget

COPY import_aws_rds_cert_bundles.sh /
RUN /import_aws_rds_cert_bundles.sh
RUN rm /import_aws_rds_cert_bundles.sh

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
