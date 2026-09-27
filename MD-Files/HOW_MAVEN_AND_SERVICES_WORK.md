# How Maven Multi-Module & Microservices Work Together

---

## Part 1: The Version Problem Maven Solves

Imagine you have 6 Spring Boot services. Without a parent POM, each service's `pom.xml` would declare:

```xml
<!-- auth-service/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>4.0.7</version>   ← you type this manually
</dependency>

<!-- ledger-service/pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>4.0.7</version>   ← you type this again
</dependency>
```

Now you want to upgrade Spring Boot. You have to update **6 files**. Miss one, and two services run on different Spring Boot versions — subtle bugs, mysterious failures, impossible to debug.

**Maven's parent POM solves this. Declare versions once, inherit everywhere.**

---

## Part 2: The Three-Level Hierarchy in This Project

This project has three levels stacked on top of each other:

```
Level 1:  org.springframework.boot : spring-boot-starter-parent : 4.0.7
               (Spring's own parent — provided by Spring team)
                              ↓ your root pom inherits from it
Level 2:  com.ledger : finledger-parent : 0.0.1-SNAPSHOT
               (YOUR root pom.xml at C:\Projects\api\pom.xml)
                              ↓ each service inherits from it
Level 3:  com.ledger : ledger-service, auth-service, etc.
               (YOUR service pom.xml files)
```

Each level inherits everything from the level above it. Let's understand each.

---

## Part 3: Level 1 — Spring Boot Starter Parent

You never write this POM. It lives inside your `.m2` local Maven repository (downloaded automatically).

**What it does for you:**

```xml
<!-- This one line in your root pom.xml activates ALL of this: -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.7</version>
</parent>
```

It gives every inheriting module:

| What It Configures | What That Means For You |
|---|---|
| Java compiler settings | `mvn package` compiles with the right Java version |
| Default encoding (UTF-8) | No charset issues on Windows vs Linux |
| Resource filtering | `application.yml` gets processed correctly |
| `<dependencyManagement>` for 200+ libraries | You can use `spring-boot-starter-web` without specifying a version |
| Maven plugin versions | `spring-boot-maven-plugin` knows how to repackage JARs |
| `maven-compiler-plugin` config | Annotation processors (Lombok) work correctly |

The most important thing it provides is a **pre-filled `<dependencyManagement>` section** for every library Spring Boot knows about. This is the Spring Boot BOM (explained in Part 5).

---

## Part 4: Level 2 — Your Root POM (`C:\Projects\api\pom.xml`)

```xml
<groupId>com.ledger</groupId>
<artifactId>finledger-parent</artifactId>
<version>0.0.1-SNAPSHOT</version>
<packaging>pom</packaging>   ← CRITICAL: this makes it a parent, not an app
```

### `<packaging>pom</packaging>` — The Key Difference

| Packaging | What Maven Does |
|---|---|
| `jar` (default) | Compiles Java source, packages into runnable JAR |
| `pom` | No compilation, no JAR — only manages child modules |

Setting `packaging=pom` tells Maven: "This is a container for other projects, not an app itself." If you forget this, Maven tries to compile the root as a Spring Boot app, finds no `src/main/java`, and fails.

### `<modules>` — Telling Maven What Exists

```xml
<modules>
    <module>eureka-service</module>
    <module>auth-service</module>
    <module>ledger-service</module>
    <module>notification-service</module>
    <module>reporting-service</module>
    <module>gateway-service</module>
</modules>
```

These are **directory names relative to the root pom**. When you run `mvn clean install` from `C:\Projects\api\`, Maven:

1. Reads the root `pom.xml`
2. Finds the `<modules>` list
3. Looks for `C:\Projects\api\eureka-service\pom.xml`, then `auth-service\pom.xml`, etc.
4. Builds them all **in the order listed**

Order matters: eureka-service is first because everything else depends on Eureka being available at runtime. Maven builds in module-list order.

### `<properties>` — Version Variables

```xml
<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2024.0.1</spring-cloud.version>
</properties>
```

These are variables. Any child module can reference `${spring-cloud.version}`. Change it here once, all 6 services automatically use the new version. `java.version` is a special property Spring Boot's parent already knows to pass to the compiler plugin.

### `<dependencyManagement>` — The Version Registry

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

`<dependencyManagement>` does **not** add any dependency to any project. It only registers "if any child asks for this library, use this version." Child modules still have to declare `<dependency>` to actually use a library — but they don't need to specify a version. `<dependencyManagement>` is the price list; `<dependencies>` is the order form.

---

## Part 5: What a BOM Is

BOM = **Bill of Materials**. It is a special POM with `<packaging>pom</packaging>` that contains only a `<dependencyManagement>` section listing dozens of libraries with their compatible, tested versions.

Spring Cloud ships a BOM called `spring-cloud-dependencies`. It looks like this internally:

```xml
<!-- This is what spring-cloud-dependencies-2024.0.1.pom looks like (simplified) -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
            <version>4.1.3</version>   ← exact compatible version
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
            <version>4.1.3</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-mvc</artifactId>
            <version>4.1.5</version>
        </dependency>
        <!-- 50+ more Spring Cloud libraries... -->
    </dependencies>
</dependencyManagement>
```

You import this BOM with `<scope>import</scope><type>pom</type>`. This merges all those version entries into your root pom's `<dependencyManagement>`. Now any child module can use any Spring Cloud library without specifying a version — the BOM already registered it.

### Spring Boot BOM vs Spring Cloud BOM

Spring Boot's parent already imports its own BOM automatically. You import Spring Cloud's BOM manually because it's a separate project.

```
spring-boot-starter-parent (Level 1)
    └── Includes spring-boot-dependencies BOM
            └── Versions for: web, jpa, kafka, redis, security, postgresql, lombok...

finledger-parent (Level 2)  
    └── Imports spring-cloud-dependencies BOM
            └── Versions for: eureka-server, eureka-client, gateway, config-server...
```

Together, your services have version-managed access to hundreds of libraries without typing a single version number.

---

## Part 6: Level 3 — Child Service POMs

Each service's `pom.xml` has one job: declare what libraries the service needs. Versions are already handled upstream.

```xml
<!-- auth-service/pom.xml -->
<parent>
    <groupId>com.ledger</groupId>
    <artifactId>finledger-parent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>   ← go up one directory to find parent
</parent>

<artifactId>auth-service</artifactId>
<packaging>jar</packaging>   ← this IS an app, produce a runnable JAR

<dependencies>
    <!-- No version needed — Spring Boot BOM already registered it -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- No version needed — Spring Cloud BOM already registered it -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>

    <!-- Version IS specified here — not in any BOM, so we own it -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.6</version>
    </dependency>
</dependencies>
```

### What `<relativePath>` Does

When Maven builds `auth-service`, it needs to find the parent POM. `<relativePath>../pom.xml</relativePath>` tells Maven: "go up one directory from auth-service/, find pom.xml there." Without this, Maven would try to download `finledger-parent` from the internet (Maven Central), fail because it's your private project, and the build breaks.

### Why Each Service Has a Different Dependency List

Each service only pulls in what it actually needs:

| Service | Key Dependencies | Why |
|---|---|---|
| `eureka-service` | `eureka-server` only | It IS the registry, doesn't need anything else |
| `auth-service` | `security` + `jpa` + `liquibase` + `jjwt` | Manages users, hashes passwords, issues JWTs |
| `ledger-service` | `jpa` + `kafka` + `redis` + `liquibase` | Manages accounts, posts transactions, caches balances |
| `notification-service` | `kafka` only | Consumes events, no database |
| `reporting-service` | `jpa` + `postgresql` | Read-only queries against ledger_db |
| `gateway-service` | `gateway-mvc` + `eureka-client` | Routes requests, no business logic |

This is the microservices principle: each service is its own deployable unit with only its own dependencies. Auth-service doesn't carry Kafka jars. Notification-service doesn't carry PostgreSQL jars. Smaller JARs, cleaner separation.

---

## Part 7: How a Full Maven Build Works

When you run `mvn clean install` from `C:\Projects\api\`:

```
Step 1: Maven reads C:\Projects\api\pom.xml
        → Sees packaging=pom, reads <modules> list

Step 2: Maven resolves the full inheritance chain for each module:
        ledger-service/pom.xml
            ↑ inherits from finledger-parent/pom.xml
                ↑ inherits from spring-boot-starter-parent-4.0.7.pom (downloaded from Maven Central)

Step 3: Maven merges all three POMs into one "effective POM" per module
        (you can see this with: mvn help:effective-pom -pl ledger-service)

Step 4: Maven builds modules in order listed in <modules>
        eureka-service → auth-service → ledger-service → notification-service → ...

Step 5: For each module with packaging=jar:
        compile → test → package → install (into local .m2 cache)

Step 6: Result: 6 runnable JAR files in each service's target/ folder
        C:\Projects\api\eureka-service\target\eureka-service-0.0.1-SNAPSHOT.jar
        C:\Projects\api\auth-service\target\auth-service-0.0.1-SNAPSHOT.jar
        ... etc
```

You can also build a single service: `mvn clean install -pl ledger-service`
Or skip tests: `mvn clean install -DskipTests`

---

## Part 8: How Services Find Each Other at Runtime (Eureka)

Maven handles build-time. Eureka handles runtime. These are completely separate concerns.

### Startup Sequence

```
1. Start eureka-service (port 8761)
   → Eureka server is now running, registry is empty

2. Start auth-service (port 8081)
   → Reads application.yml: eureka.client.serviceUrl.defaultZone=http://localhost:8761/eureka/
   → On startup, sends HTTP POST to Eureka: "I am 'auth-service' at 192.168.1.5:8081"
   → Eureka adds it to the registry
   → auth-service polls Eureka every 30s to stay registered (heartbeat)

3. Start ledger-service (port 8082)
   → Same process — registers itself as 'ledger-service' at port 8082

4. Start gateway-service (port 8080)
   → Registers itself
   → Fetches the full registry from Eureka (all known services + their addresses)
   → Caches the registry locally, refreshes every 30s

5. A request arrives: POST http://localhost:8080/api/transactions
   → Gateway reads its route config: /api/** → lb://ledger-service
   → lb:// means "load-balanced" — ask the local Eureka registry cache
   → Registry says: ledger-service is at 192.168.1.5:8082
   → Gateway forwards the request to http://192.168.1.5:8082/api/transactions
```

### Why This Matters

Without Eureka, the gateway would have:
```yaml
routes:
  - uri: http://localhost:8082   ← hardcoded — breaks in Docker, breaks on different machines
```

With Eureka, the gateway has:
```yaml
routes:
  - uri: lb://ledger-service   ← dynamic — works anywhere, supports multiple instances
```

If you run 3 instances of ledger-service, the gateway automatically load-balances across all 3. Eureka keeps track of which instances are alive.

---

## Part 9: How application.yml Configures Each Service

Every service has its own `src/main/resources/application.yml`. Spring Boot reads this file at startup and configures the service based on it. No two services share a config file.

### The Key Properties Per Service

**eureka-service** — tells Eureka NOT to register itself (it IS the registry):
```yaml
eureka:
  client:
    registerWithEureka: false   ← don't register with yourself
    fetchRegistry: false        ← don't fetch your own registry
```

**All other services** — tells them where Eureka lives:
```yaml
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
```

**auth-service** — gets its own database:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auth_db   ← separate DB from ledger
```

**ledger-service** — different database:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ledger    ← its own DB
```

**Each service runs on a different port** — they're separate processes:
```
eureka-service:      8761
gateway-service:     8080
auth-service:        8081
ledger-service:      8082 (default, not set explicitly yet)
notification-service: 8083
reporting-service:   8084
```

---

## Part 10: The Complete Picture

```
────────────────── BUILD TIME (Maven) ──────────────────

spring-boot-starter-parent (Spring's POM)
    └── Provides: compiler config, 200+ library versions
            ↓ inherited by
    finledger-parent (your root pom.xml)
        ├── Imports: spring-cloud-dependencies BOM (50+ Spring Cloud versions)
        ├── Declares: java.version=17, spring-cloud.version=2024.0.1
        └── Lists modules: eureka, auth, ledger, notification, reporting, gateway
                ↓ each inherits from finledger-parent
        ├── eureka-service/pom.xml    → pulls eureka-server           → eureka-service.jar
        ├── auth-service/pom.xml      → pulls security+jpa+jjwt       → auth-service.jar
        ├── ledger-service/pom.xml    → pulls jpa+kafka+redis          → ledger-service.jar
        ├── notification-service/pom.xml → pulls kafka               → notification-service.jar
        ├── reporting-service/pom.xml → pulls jpa                     → reporting-service.jar
        └── gateway-service/pom.xml   → pulls gateway-mvc             → gateway-service.jar

─────────────────── RUNTIME (JVM + Eureka) ──────────────────

[eureka-service.jar :8761] ← starts first, registry is empty

[auth-service.jar :8081] ──registers──→ Eureka
[ledger-service.jar :8082] ─registers──→ Eureka
[notification-service.jar :8083] ──────→ Eureka
[reporting-service.jar :8084] ─────────→ Eureka
[gateway-service.jar :8080] ────────────→ Eureka

External Request
    ↓
[gateway :8080]
    ├─ /auth/**  ──lb://auth-service──→  [auth-service :8081]  ──→ auth_db
    ├─ /api/**   ──lb://ledger-service─→ [ledger-service :8082] ──→ ledger_db
    │                                                             ──→ Redis
    │                                                             ──→ Kafka ──→ [notification :8083]
    └─ /reports/** ─lb://reporting────→ [reporting-service :8084] ──→ ledger_db (read-only)
```

---

## Part 11: Upgrade Checklist — The Single File to Change

When Spring Boot or Spring Cloud releases a new version, you change **one file**:

```xml
<!-- C:\Projects\api\pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.8</version>   ← change here → all 6 services upgraded
</parent>

<properties>
    <spring-cloud.version>2024.0.2</spring-cloud.version>  ← change here → all Eureka/Gateway versions updated
</properties>
```

Every child service picks up the new versions automatically on the next `mvn clean install`. No hunting through 6 pom.xml files. This is the entire point of the multi-module structure.

---

## Summary

| Concept | File | Purpose |
|---|---|---|
| Spring Boot BOM | Inside `spring-boot-starter-parent` (downloaded) | Versions for all Spring Boot libraries |
| Spring Cloud BOM | `spring-cloud-dependencies` (imported in root pom) | Versions for Eureka, Gateway, Config, etc. |
| Parent POM | `C:\Projects\api\pom.xml` | Owns version numbers, lists all modules |
| Child POM | Each `service/pom.xml` | Declares what the service needs, no versions |
| `packaging=pom` | Root pom only | Makes Maven treat it as a container, not an app |
| `packaging=jar` | Each service pom | Makes Maven produce a runnable Spring Boot JAR |
| Eureka | Runtime only — nothing to do with Maven | Services find each other by name, not IP |
| `application.yml` | Each service's `src/main/resources/` | Runtime config: which port, which DB, where is Eureka |
