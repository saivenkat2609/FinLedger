# How Docker, Dockerfiles & Docker Compose Work in This Project

---

## What Was Added (File List)

```
C:\Projects\api\
  ├── .dockerignore                        ← tells Docker what NOT to copy into builds
  ├── docker-compose.yml                   ← runs ALL 11 containers with one command
  ├── docker/
  │   └── init-postgres.sh                 ← creates auth_db inside PostgreSQL on first start
  ├── eureka-service/Dockerfile            ← builds eureka-service into a runnable image
  ├── auth-service/Dockerfile              ← builds auth-service into a runnable image
  ├── ledger-service/Dockerfile            ← builds ledger-service into a runnable image
  ├── notification-service/Dockerfile      ← builds notification-service into a runnable image
  ├── reporting-service/Dockerfile         ← builds reporting-service into a runnable image
  └── gateway-service/Dockerfile           ← builds gateway-service into a runnable image
```

---

## Part 1: The Big Picture

When you run `docker-compose up --build` from the project root, here is exactly what happens:

```
docker-compose reads docker-compose.yml
        ↓
Builds Docker images for all 6 Spring Boot services (using their Dockerfiles)
        ↓
Starts 11 containers in dependency order:

  [1] postgres      ← database server
  [2] redis         ← cache server
  [3] zookeeper     ← Kafka coordinator
  [4] kafka         ← message broker (waits for zookeeper to be healthy)
  [5] kafdrop       ← Kafka UI (waits for kafka)
  [6] eureka-service ← service registry (waits for nothing — starts right away)
  [7] auth-service  ← waits for postgres + eureka to be healthy
  [8] ledger-service ← waits for postgres + redis + kafka + eureka
  [9] notification-service ← waits for kafka + eureka
  [10] reporting-service   ← waits for postgres + eureka
  [11] gateway-service     ← waits for eureka (last to start)
```

Each container is an isolated process. They talk to each other over an internal Docker network called `ledger_network` using their **service names as hostnames**.

---

## Part 2: How a Dockerfile Works

A Dockerfile is a recipe for building a Docker image. Think of a Docker image as a frozen snapshot of an operating system + your app JAR + Java runtime, ready to run anywhere.

Every Dockerfile in this project uses a **two-stage build**:

```
Stage 1 (builder): Maven + JDK → compiles Java source → produces JAR
Stage 2 (runtime): JRE only   → copies JAR from Stage 1 → runs it
```

Why two stages? Because Maven + JDK together are ~500MB. The final image only needs the JRE (~100MB) to run the JAR. Stage 1 is thrown away after the JAR is built. This makes the production image 5x smaller.

---

## Part 3: Anatomy of One Dockerfile (auth-service)

```dockerfile
# syntax=docker/dockerfile:1
```
Tells Docker to use BuildKit (modern Docker build engine). Required for the `--mount=type=cache` feature later.

---

```dockerfile
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace
```
**Stage 1 begins.** Pull the official Maven image (which includes JDK 17) from Docker Hub. Set `/workspace` as the working directory inside the container. Name this stage `builder`.

---

```dockerfile
COPY pom.xml .
COPY eureka-service/pom.xml      eureka-service/pom.xml
COPY auth-service/pom.xml        auth-service/pom.xml
COPY ledger-service/pom.xml      ledger-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY reporting-service/pom.xml   reporting-service/pom.xml
COPY gateway-service/pom.xml     gateway-service/pom.xml
```
Copy ALL `pom.xml` files from your machine into the container.

**Why ALL of them?** When Maven reads the root `pom.xml`, it sees `<modules>` listing all 6 services. Maven tries to read each module's `pom.xml` to build its internal dependency map (called the "reactor"). If ANY module's `pom.xml` is missing, Maven fails. So we copy all 7 pom files, even though we only build one service.

**Why only pom files first (not src)?** Docker builds in layers. Each `COPY` and `RUN` instruction creates a layer. Docker caches layers. If the pom files haven't changed, Docker reuses the cached layers from the previous build and skips re-downloading dependencies. This makes rebuilds fast.

---

```dockerfile
COPY auth-service/src auth-service/src
```
Copy only **this service's** source code. Not ledger-service's src, not gateway-service's src — only auth-service's. If ledger-service's code changes and you rebuild auth-service, this layer is unaffected.

---

```dockerfile
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -pl auth-service --also-make -DskipTests -B -q
```
This is the build command. Let's break each part:

| Part | Meaning |
|---|---|
| `--mount=type=cache,target=/root/.m2` | BuildKit caches the Maven local repository between builds. Downloaded JARs are NOT re-downloaded on next build |
| `mvn package` | Compile, test (skipped), and package into a JAR |
| `-pl auth-service` | Only build the `auth-service` module (not all 6) |
| `--also-make` | Also build anything auth-service depends on (the parent POM) |
| `-DskipTests` | Skip tests — CI runs them separately |
| `-B` | Non-interactive (batch mode) — better logs in Docker |
| `-q` | Quiet — suppress Maven's verbose output |

Result: `auth-service/target/auth-service-0.0.1-SNAPSHOT.jar` is created inside the container.

---

```dockerfile
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
```
**Stage 2 begins.** Fresh, clean image with only the Java Runtime Environment (no compiler, no Maven). Alpine Linux keeps it small. Install `curl` — needed for the health checks in docker-compose.

---

```dockerfile
COPY --from=builder /workspace/auth-service/target/auth-service-*.jar app.jar
```
Copy the JAR built in Stage 1 into this clean image. The `*` wildcard handles the version number (e.g., `-0.0.1-SNAPSHOT`). Stage 1's 500MB Maven image is completely discarded after this.

---

```dockerfile
EXPOSE 8081
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```
`EXPOSE 8081` — documents which port this service listens on. Does not actually open it — that's docker-compose's job.

`-XX:+UseContainerSupport` — tells the JVM to respect Docker's memory limits instead of reading the host machine's total RAM. Without this, the JVM might try to use 25% of your 32GB host RAM when the container only has 512MB.

`-XX:MaxRAMPercentage=75` — JVM can use up to 75% of the container's allocated memory for the heap.

---

## Part 4: How Docker Layer Caching Saves Time

The first `docker-compose up --build` takes 10-15 minutes (downloads Maven, Spring Boot JARs). After that, rebuilds are fast because Docker caches layers:

```
Scenario: You only changed auth-service's Java code

Layer 1: FROM maven:...                → CACHED (image already local)
Layer 2: COPY pom.xml .                → CACHED (pom unchanged)
Layer 3: COPY auth-service/pom.xml ... → CACHED (pom unchanged)
...all 7 pom COPYs...                  → CACHED
Layer 8: COPY auth-service/src ...     → INVALIDATED (source changed!)
Layer 9: RUN mvn package ...           → RE-RUNS (but .m2 cache is warm)

Result: Maven skips downloading JARs, only recompiles changed classes.
Rebuild time: ~30 seconds instead of 10 minutes.
```

If you change a pom.xml, Layer 3 is invalidated and Maven re-downloads that service's dependencies. Still faster than a cold build because the Maven cache mount (`/root/.m2`) persists across all builds.

---

## Part 5: What .dockerignore Does

```
.git
.idea
**/target
**/*.log
**/logs
*.md
```

When Docker starts a build, it first sends the entire **build context** (the directory you specified) to the Docker daemon. Without `.dockerignore`, this would include:
- `.git/` — hundreds of MB of git history → useless in an image
- `**/target/` — compiled classes from your host machine → wrong, Maven should compile inside Docker
- `*.log` — log files → irrelevant

`.dockerignore` tells Docker to exclude these. Result: The build context sent to Docker is small (~1MB just source and config), making `docker-compose up --build` start faster.

---

## Part 6: docker/init-postgres.sh

```bash
#!/bin/bash
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE auth_db;
    GRANT ALL PRIVILEGES ON DATABASE auth_db TO $POSTGRES_USER;
EOSQL
```

The PostgreSQL Docker image has a feature: any `.sh` or `.sql` file placed in `/docker-entrypoint-initdb.d/` runs automatically on **first container start** (only when the data directory is empty/new).

In docker-compose.yml, we mount this script:
```yaml
volumes:
  - ./docker/init-postgres.sh:/docker-entrypoint-initdb.d/init-postgres.sh:ro
```

**What it does:**
- `POSTGRES_DB: ledger` in docker-compose already creates the `ledger` database
- This script creates the second database: `auth_db`
- `$POSTGRES_USER` and `$POSTGRES_DB` are environment variables set by docker-compose

**Result:** PostgreSQL starts with two databases: `ledger` (for ledger-service) and `auth_db` (for auth-service).

**Important:** This script only runs once — on the very first `docker-compose up`. If you run it again, the data volume (`postgres_data`) already exists and the script is skipped. To re-run it, delete the volume: `docker-compose down -v`.

---

## Part 7: Anatomy of docker-compose.yml

The file has three sections: infrastructure, Spring Boot services, and volumes/networks.

### Infrastructure Services

```yaml
postgres:
  image: postgres:15-alpine       ← use this pre-built image from Docker Hub
  container_name: ledger_postgres ← friendly name for docker ps
  environment:
    POSTGRES_USER: ledger
    POSTGRES_PASSWORD: ledger123
    POSTGRES_DB: ledger           ← creates this DB automatically on first start
  ports:
    - "5432:5432"                 ← host_port:container_port (access from your machine)
  volumes:
    - postgres_data:/var/lib/postgresql/data         ← persist DB between restarts
    - ./docker/init-postgres.sh:/docker-entrypoint-initdb.d/...:ro  ← run on first start
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U ledger"]
    interval: 10s   ← check every 10 seconds
    timeout: 5s     ← give up if no response in 5s
    retries: 5      ← mark as unhealthy after 5 failures
  networks:
    - ledger_network  ← join this private network
```

The **healthcheck** is what makes `depends_on: condition: service_healthy` work. Without it, Docker would start the next service immediately after postgres starts (but before it's actually ready to accept connections).

---

### Kafka Setup (Two Listeners Explained)

```yaml
kafka:
  environment:
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:9093
```

Kafka needs two listener addresses because it's accessed from two different places:

| Listener | Address | Used By |
|---|---|---|
| `PLAINTEXT` | `kafka:9092` | Other Docker containers (inside `ledger_network`) |
| `PLAINTEXT_HOST` | `localhost:9093` | Your host machine (Postman, local Spring Boot dev) |

Inside Docker, containers use `kafka:9092` because `kafka` resolves to the Kafka container's internal IP via Docker DNS. From your laptop, you can't resolve `kafka` as a hostname, so you use `localhost:9093` which is mapped to the container's 9093 port.

This is why:
- `application.yml` (local dev): `bootstrap-servers: localhost:9093`
- docker-compose env vars (in Docker): `SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092`

---

### Spring Boot Services in docker-compose

```yaml
auth-service:
  build:
    context: .                          ← build context is project root (not auth-service/)
    dockerfile: auth-service/Dockerfile ← Dockerfile location relative to context
  container_name: ledger_auth
  ports:
    - "8081:8081"                       ← your machine:8081 → container:8081
  depends_on:
    postgres:
      condition: service_healthy        ← wait for postgres healthcheck to pass
    eureka-service:
      condition: service_healthy        ← wait for eureka to be fully up
  environment:
    # These override the localhost values in application.yml
    # Spring Boot converts SPRING_DATASOURCE_URL → spring.datasource.url
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/auth_db
    SPRING_DATASOURCE_USERNAME: ledger
    SPRING_DATASOURCE_PASSWORD: ledger123
    EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-service:8761/eureka/
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
    interval: 15s
    timeout: 5s
    retries: 5
    start_period: 90s   ← don't start health checking until 90s after container starts
                        ← Spring Boot takes 30-60s to start, so we give it breathing room
  networks:
    - ledger_network
```

### Why `context: .` (Root) Instead of `context: auth-service/`?

The Dockerfile needs to COPY files from multiple directories:
```dockerfile
COPY pom.xml .                    ← root level
COPY auth-service/pom.xml ...     ← inside auth-service/
COPY ledger-service/pom.xml ...   ← inside ledger-service/
```

`COPY` paths are **relative to the build context**, not the Dockerfile. If context were `auth-service/`, `COPY pom.xml` would look for `auth-service/pom.xml` not the root one. By setting `context: .` (root), Docker sees the whole project tree and all COPY paths work correctly.

### How Environment Variables Override application.yml

Spring Boot has a property binding rule: any environment variable in `UPPER_SNAKE_CASE` automatically overrides the matching `application.yml` key.

The conversion: dots (`.`) and hyphens (`-`) become underscores (`_`), everything uppercase.

```
application.yml key                      →  Environment Variable
─────────────────────────────────────────────────────────────────
spring.datasource.url                    →  SPRING_DATASOURCE_URL
spring.datasource.username               →  SPRING_DATASOURCE_USERNAME
spring.data.redis.host                   →  SPRING_DATA_REDIS_HOST
spring.kafka.bootstrap-servers           →  SPRING_KAFKA_BOOTSTRAP_SERVERS
eureka.client.serviceUrl.defaultZone     →  EUREKA_CLIENT_SERVICEURL_DEFAULTZONE
server.port                              →  SERVER_PORT
```

So `application.yml` has `localhost` values (for running locally), and docker-compose injects the correct Docker-internal hostnames as environment variables. **Same code, different config per environment.**

---

## Part 8: Ports — Who Talks to Whom

```
Your Machine (host)          Docker Internal (ledger_network)
──────────────────           ───────────────────────────────
localhost:5432    ←map→      postgres:5432
localhost:6379    ←map→      redis:6379
localhost:9093    ←map→      kafka:9093   (PLAINTEXT_HOST)
                             kafka:9092   (PLAINTEXT — internal only)
localhost:9000    ←map→      kafdrop:9000
localhost:8761    ←map→      eureka-service:8761
localhost:8081    ←map→      auth-service:8081
localhost:8082    ←map→      ledger-service:8082
localhost:8083    ←map→      notification-service:8083
localhost:8084    ←map→      reporting-service:8084
localhost:8080    ←map→      gateway-service:8080
```

Containers on `ledger_network` use each other's **service names** as hostnames (Docker's built-in DNS). `auth-service` connects to Postgres using `postgres:5432` — Docker resolves `postgres` to the postgres container's internal IP automatically.

---

## Part 9: depends_on and Startup Order

```yaml
gateway-service:
  depends_on:
    eureka-service:
      condition: service_healthy
```

`depends_on` with `condition: service_healthy` means:
1. Docker starts `eureka-service`
2. Docker waits for eureka's healthcheck to return HTTP 200
3. Only then does Docker start `gateway-service`

Without `condition: service_healthy` (just `depends_on: - eureka-service`), Docker would start gateway-service the moment eureka-service's container process starts — but Spring Boot takes 60-90 seconds to initialize. Gateway would crash trying to contact an Eureka that isn't ready yet.

The full startup chain:
```
postgres ──healthy──┐
                    ├──→ auth-service ──healthy──┐
eureka   ──healthy──┘                            │
                                                 ├──→ (all services up)
redis    ──healthy──┐                            │
kafka    ──healthy──┼──→ ledger-service ─────────┘
eureka   ──healthy──┘
```

---

## Part 10: The Full Commands

```bash
# First time — builds all images and starts all containers
docker-compose up --build

# Start without rebuilding (if images already exist)
docker-compose up

# Start in background (detached mode)
docker-compose up -d --build

# Stop everything (keeps data volumes)
docker-compose down

# Stop everything AND delete database data (fresh start)
docker-compose down -v

# Rebuild only one service
docker-compose build ledger-service
docker-compose up -d ledger-service   # restart just that one

# See logs
docker-compose logs -f ledger-service

# Check what's running
docker-compose ps
```

---

## Part 11: Summary Table

| File | What It Is | What It Does |
|---|---|---|
| `Dockerfile` (×6) | Build recipe per service | Maven compiles Java → JAR; JRE image runs it |
| `docker-compose.yml` | Orchestration config | Starts all 11 containers in right order with right config |
| `docker/init-postgres.sh` | DB init script | Creates `auth_db` alongside `ledger` DB on first start |
| `.dockerignore` | Build filter | Excludes `.git`, `target/`, logs from Docker build context |

| Concept | Rule |
|---|---|
| Build context is always `.` (root) | Dockerfile needs files from multiple service directories |
| All pom.xml files copied, only one `src/` | Maven needs full reactor to build one module |
| `--mount=type=cache` | Maven `.m2` cache persists between Docker builds |
| Two Kafka listeners | Internal containers use `kafka:9092`, host uses `localhost:9093` |
| `start_period: 90s` on healthchecks | Spring Boot needs time to start before health checks begin |
| Env vars override application.yml | Same JAR runs locally (localhost) and in Docker (container names) |
