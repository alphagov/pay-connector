FROM java:8-jre
ENV JAVA_HOME /usr/lib/jvm/java-8-*/

ADD target/pay-*-allinone.jar /app/
ADD target/*.yaml /app/
WORKDIR /app

ENV PORT 9000
ENV ADMIN_PORT 9300
EXPOSE 9000 9300
CMD java -jar pay-*-allinone.jar server *.yaml
