# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
RUN apk add --no-cache ca-certificates && update-ca-certificates
COPY docker/maven.cer /tmp/maven.cer
RUN keytool -import -alias maven-cert -file /tmp/maven.cer -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -noprompt
WORKDIR /workspace

COPY pom.xml .
COPY eureka-service/pom.xml      eureka-service/pom.xml
COPY auth-service/pom.xml        auth-service/pom.xml
COPY ledger-service/pom.xml      ledger-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY gateway-service/pom.xml     gateway-service/pom.xml

COPY gateway-service/src gateway-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn package -pl gateway-service --also-make -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/gateway-service/target/gateway-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
