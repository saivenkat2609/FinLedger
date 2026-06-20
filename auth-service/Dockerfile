# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
COPY eureka-service/pom.xml      eureka-service/pom.xml
COPY auth-service/pom.xml        auth-service/pom.xml
COPY ledger-service/pom.xml      ledger-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY reporting-service/pom.xml   reporting-service/pom.xml
COPY gateway-service/pom.xml     gateway-service/pom.xml

COPY auth-service/src auth-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn package -pl auth-service --also-make -DskipTests -B -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/auth-service/target/auth-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
