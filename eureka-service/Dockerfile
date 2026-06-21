# syntax=docker/dockerfile:1
# Build context must be the project root: docker-compose sets context: .
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
RUN apk add --no-cache ca-certificates && update-ca-certificates
COPY docker/maven.cer /tmp/maven.cer
RUN keytool -import -alias maven-cert -file /tmp/maven.cer -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit -noprompt
WORKDIR /workspace

# Copy all POMs so Maven can read the full multi-module reactor
COPY pom.xml .
COPY eureka-service/pom.xml      eureka-service/pom.xml
COPY auth-service/pom.xml        auth-service/pom.xml
COPY ledger-service/pom.xml      ledger-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY gateway-service/pom.xml     gateway-service/pom.xml

# Copy source for this service only
COPY eureka-service/src eureka-service/src

# Build only this module and its Maven parents (--also-make)
# --mount=type=cache keeps downloaded deps cached across rebuilds
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -pl eureka-service --also-make -DskipTests -B -q

# ---- Runtime image ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/eureka-service/target/eureka-service-*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
