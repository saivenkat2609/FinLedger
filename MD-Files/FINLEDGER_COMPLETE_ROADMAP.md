# FinLedger: Production-Grade Microservices Payment Ledger
## Complete Feature-by-Feature Build Guide

**Build a production-grade financial backend by implementing microservices, one feature at a time**

**Stack: Java 21 · Spring Boot 4.0 · PostgreSQL · Redis · Kafka · Spring Security · JWT · Liquibase · Docker Compose · Eureka**

---

## ✅ PROGRESS TRACKER

### Completed
*(nothing yet — you haven't started)*

### In Progress
- [ ] Phase 0: Foundation Setup (Microservices + Infrastructure)

### Pending (Core — Essential for Production)
- [ ] Feature 1: Auth Service (Spring Security + JWT)
- [ ] Feature 1.5: API Gateway (Request routing, JWT validation, middleware)
- [ ] Feature 2: Ledger Service (Accounts & Core Logic)
- [ ] Feature 3: Double-Entry Transactions
- [ ] Feature 4: Transaction State Machine & Immutability
- [ ] Feature 5: Idempotency Protection
- [ ] Feature 6: Async Events (Kafka)
- [ ] Feature 7: Reconciliation Engine
- [ ] Feature 8: Performance — Redis Caching
- [ ] Feature 9: Observability & Polish
- [ ] Feature 10: Resilience & Error Handling (Circuit breakers, retries)
- [ ] Feature 11: Search, Filtering & Pagination
- [ ] Feature 12: Audit Logging & Compliance

### Optional Advanced (Nice-to-Have for Scale)
- [ ] Feature 13: Transaction Limits & Risk Management (fraud prevention)
- [ ] Feature 14: Fee Management (calculating & deducting fees)
- [ ] Feature 15: API Versioning (v1, v2 support)
- [ ] Feature 16: Role-Based Access Control (RBAC) — fine-grained permissions
- [ ] Feature 17: Webhook Support (push events to external systems)
- [ ] Feature 18: Request Input Validation & Sanitization (injection prevention)
- [ ] Feature 19: Database Backup & Disaster Recovery (point-in-time recovery)
- [ ] Feature 20: Load Testing & Performance Benchmarking

---

## 🎯 Development Philosophy

### ❌ Wrong Approach
```
Week 1: Design all microservices
Week 2: Build all services in parallel (chaos)
Week 3: Try to connect them (nothing talks to anything)
Week 4-5: Debug distributed systems issues, give up
```

### ✅ Right Approach
```
Phase 0: All services run locally, basic health checks pass
Feature 1: Auth Service works end-to-end with JWT
Feature 2: Ledger Service accounts API works
Feature 3: Transactions post correctly + tests pass
Feature 4-9: Each feature works, is tested, integrates with prior features
Result: Production-ready microservices that interview panels are impressed by
```

---

## 🏆 Your Edge in Fintech Interviews

What separates your ledger from every other candidate's:

| What You'll Build | Why It Matters |
|---|---|
| **Microservices with Eureka discovery** | "I've built distributed systems" — not a monolith |
| **Spring Security + JWT authentication** | "I understand modern API security" — not basic auth |
| **Kafka event streaming with DLT** | "I've handled async failures" — production patterns |
| **True idempotency with race conditions** | Two requests at same millisecond? Handled. |
| **Immutable journal + reconciliation** | "I understand fintech compliance" — not just code |
| **Correlation IDs end-to-end** | "I can debug production issues" — traceability matters |

When an interviewer asks about this, lead with: *"I built a microservices payment system with Spring Security, Kafka event streaming, and distributed tracing — here's how I solved X hard problem..."*

---

## 📚 What You'll Learn — Phase by Phase

| Feature | Core Concepts | What It Produces |
|---------|-------------|-----------------|
| Phase 0 | Spring Boot structure, Docker, Microservices, Liquibase | 5 services running locally |
| Feature 1 | Spring Security, BCrypt, JWT tokens, Spring Cloud Eureka | Auth Service with login/register |
| Feature 2 | Domain modeling, Spring Data JPA, repository pattern | Accounts API, list accounts |
| Feature 3 | Double-entry bookkeeping, ACID, @Transactional | Post transfers between accounts |
| Feature 4 | State machines, immutability, reversals | Transaction lifecycle enforcement |
| Feature 5 | Idempotency, race conditions, INSERT ... ON CONFLICT | Duplicate-safe payment API |
| Feature 6 | Event-driven architecture, Kafka, Dead Letter Topic | Async settlement pipeline |
| Feature 7 | Reconciliation, discrepancy detection | Verify books always balance |
| Feature 8 | Cache-aside pattern, Redis, Spring Cache | Sub-50ms balance reads |
| Feature 9 | Structured logging, MDC correlation IDs, Actuator | Full traceability + healthchecks |

---

## 🏗️ Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                      API CONSUMER                            │
│                                                              │
│   Web Client / Mobile / Postman → /auth/* (no token yet)   │
│   Then → /api/* (with JWT token in header)                  │
└──────────────────────────────────────────────────────────────┘
                             ↕ HTTP
┌──────────────────────────────────────────────────────────────┐
│                   API GATEWAY (Port 8080)                    │
│                                                              │
│   ├─ CorrelationIdFilter → inject X-Correlation-ID          │
│   ├─ JwtAuthenticationFilter → validate JWT                 │
│   ├─ RateLimitingFilter → check Redis bucket                │
│   └─ RouteLocator → load-balance to services via Eureka     │
└──────────────────────────────────────────────────────────────┘
        ↓ to Auth Service        ↓ to Ledger Service
┌──────────────────┐        ┌──────────────────────┐
│  AUTH SERVICE    │        │  LEDGER SERVICE      │
│   (Port 8081)    │        │   (Port 8082)        │
│                  │        │                      │
│ POST /register   │        │ POST /accounts       │
│ POST /login      │        │ GET  /accounts/{id}  │
│ POST /refresh    │        │ POST /transactions   │
│                  │        │ GET  /transactions   │
└────────┬─────────┘        └──────────┬───────────┘
         │                             │
    ┌────▼──────┐              ┌──────▼─────────┐
    │ auth_db   │              │  ledger_db     │
    │           │              │                │
    │ users     │              │ accounts       │
    │ tokens    │              │ transactions   │
    └───────────┘              │ journal_entries│
                               └──────┬─────────┘
                                      │
                               ┌──────▼──────────┐
                               │ Publishes to    │
                               │ KAFKA:9092      │
                               │ Topics:         │
                               │ - txn-events    │
                               │ - user-events   │
                               └──────┬──────────┘
                                      │
        ┌─────────────────────────────┴──────────────────────┐
        │                                                     │
   ┌────▼──────────────────┐                  ┌─────▼────────────────┐
   │ NOTIFICATION SERVICE  │                  │ REPORTING SERVICE    │
   │   (Port 8083)         │                  │   (Port 8084)        │
   │                       │                  │                      │
   │ @KafkaListener        │                  │ GET /reports/        │
   │ Consumes txn-events   │                  │ (read-only analytics)│
   │ Sends emails/alerts   │                  │                      │
   └───────────────────────┘                  └──────────────────────┘

         ┌──────────────────────────────────────────────┐
         │    SHARED INFRASTRUCTURE                     │
         ├──────────────────────────────────────────────┤
         │ • Eureka Service Registry :8761              │
         │ • Redis (Cache) :6379                        │
         │ • Zookeeper (Kafka coord) :2181              │
         │ • Kafka Broker :9092                         │
         │ • PostgreSQL :5432 (4 separate DBs)          │
         └──────────────────────────────────────────────┘
```

---

## 🔑 Core Concepts — Read This First

### Account
A named container that holds a balance. Examples: a user's wallet, a merchant's payout account, a fees collection account. Every account has a **type** (Asset, Liability, Income, Expense) and a running balance. Accounts are never deleted, only deactivated.

### Journal Entry
The atomic, write-once unit of the ledger. Records: which account it affects, the amount, whether it's a debit or credit, and a timestamp. **Journal entries are never updated or deleted** — this is the most important rule.

### Transaction
A group of two or more journal entries that must all succeed or all fail together (atomically). A ₹500 transfer is one transaction with two journal entries: -₹500 debit on Account A, +₹500 credit on Account B.

### Reconciliation
The process of verifying that for any set of complete transactions, the sum of all debit entries equals the sum of all credit entries. Real fintech companies run this every single day.

### JWT Token
An encoded token containing user identity and roles, signed by the Auth Service. Gateway validates every request's JWT before routing to services. If invalid → 401 Unauthorized.

### Correlation ID
A unique identifier assigned to each API request that flows through all logs, database operations, and Kafka events. Search one ID and trace the entire transaction lifecycle across all services.

---

# 🚀 PHASE 0: Foundation Setup (Microservices + Infrastructure)

## Goal
Get all 6 microservices running locally end-to-end with Docker Compose. Every service is up, every health check passes, and you understand how they talk to each other.

## What You're Building
```
5 Spring Boot applications:
  ├─ Auth Service (Port 8081) — JWT generation + validation
  ├─ Ledger Service (Port 8082) — Core accounting engine
  ├─ Notification Service (Port 8083) — Kafka consumer
  ├─ API Gateway (Port 8080) — Request routing + validation
  └─ Eureka Registry (Port 8761) — Service discovery

All talking through Docker Compose:
  PostgreSQL :5432 (2 separate DBs — auth_db, ledger)
  Redis :6379
  Kafka :9092
  Zookeeper :2181
```

---

## Step 0.1: Initialize the Monorepo Structure

### What
Create a root `pom.xml` that manages all 6 services as Maven modules, and a `docker-compose.yml` that starts everything together

### Why
Each microservice is a separate Spring Boot app, but they share dependencies and deploy together in docker-compose. A parent `pom.xml` ensures all services use the same Spring Boot version and dependency versions — no version drift between services.

### Concept: Maven Modules
A parent POM with `<packaging>pom</packaging>` and `<modules>` lets you run `mvn clean install` once and build all 6 services. In the parent POM:
- Define Spring Boot version, Spring Cloud version, Jakarta persistence version
- Define common properties (Java version, Kafka version, PostgreSQL driver)
- Each service inherits these versions — no need to repeat them

### High-Level Approach
Create directory structure:
```
project-root/
  ├── pom.xml (parent)
  ├── docker-compose.yml
  ├── auth-service/
  │   ├── pom.xml (inherits from parent)
  │   └── src/main/java/com/ledger/auth/
  ├── ledger-service/
  │   ├── pom.xml
  │   └── src/main/java/com/ledger/api/
  ├── notification-service/
  │   ├── pom.xml
  │   └── src/main/java/com/ledger/notification/
  ├── reporting-service/
  │   ├── pom.xml
  │   └── src/main/java/com/ledger/reporting/
  ├── gateway-service/
  │   ├── pom.xml
  │   └── src/main/java/com/ledger/gateway/
  └── eureka-service/
      ├── pom.xml
      └── src/main/java/com/ledger/eureka/
```

Parent `pom.xml` should:
- Have `<packaging>pom</packaging>`
- List all 6 services in `<modules>`
- Define a `<dependencyManagement>` section with Spring Boot BOM and versions for Kafka, PostgreSQL, JJWT, Spring Cloud
- NOT include application code, just configuration

✅ **Checkpoint:** Run `mvn clean install` from root — all 6 modules compile successfully with no dependency conflicts.

---

## Step 0.2: Create Eureka Service Registry

### What
A standalone Spring Boot app that runs on port 8761 and serves as the service registry where all other services register themselves

### Why
In microservices, services need to discover each other dynamically. Eureka replaces hardcoded service URLs. When Ledger Service wants to call Auth Service, it asks Eureka: "Where is auth-service?" Eureka responds with the hostname and port.

### Concept: Service Registration
When Auth Service starts, it announces to Eureka: "I am auth-service running on localhost:8081". When Ledger Service starts, it tells Eureka: "I am ledger-service running on localhost:8082". The API Gateway asks Eureka for routes and load-balances between them.

### High-Level Approach
Create `eureka-service/` module with Spring Cloud Eureka Server starter. Add `@EnableEurekaServer` to main application class. Configure `application.yml` to disable self-registration (Eureka itself doesn't register with itself). The service is stateless and can be restarted without affecting other services.

✅ **Checkpoint:** Start eureka-service. Visit `http://localhost:8761` — dashboard shows "No registered instances yet". This is correct.

---

## Step 0.3: Set Up Docker Compose

### What
A single `docker-compose.yml` that starts PostgreSQL, Redis, Kafka, Zookeeper, and Docker image builds for all 6 services

### Why
You never want to install databases on your machine. Docker gives reproducibility — anyone cloning the repo runs the same services.

### Concept: Service Dependencies
Define services in order of dependency:
1. postgres, redis, zookeeper (base infrastructure)
2. kafka (depends on zookeeper)
3. eureka-service (no external dependencies, starts first)
4. auth-service, ledger-service, notification-service, reporting-service, gateway (all depend on eureka)

Each service gets environment variables for connecting to the others. For example, Ledger Service needs: `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` and `EUREKA_URL=http://eureka-service:8761/eureka`.

### High-Level Approach
`docker-compose.yml` should define:
- `postgres`: PostgreSQL image, expose port 5432, create multiple databases (auth_db, ledger_db) via init SQL
- `redis`: Redis image, expose port 6379
- `zookeeper`: For Kafka coordination
- `kafka`: Kafka broker, advertise as `kafka:9092` internally
- `eureka-service`: Build from Dockerfile, expose 8761
- `auth-service` through `reporting-service`: Each builds from Dockerfile, links to other services
- `gateway-service`: Last in the list, routes to all others

Each service needs:
- Proper environment variables for database URLs, Kafka bootstrap servers, Eureka URL
- Correct depends_on order
- Health checks (optional but recommended)

✅ **Checkpoint:** Run `docker-compose up --build` from root. All services start. Eureka dashboard at `http://localhost:8761` shows 5 services registered (excluding Eureka itself).

---


## Step 0.4: Configure Liquibase for Each Service

### What
Each service that needs a database (Auth, Ledger) gets its own Liquibase migration configuration

### Why
Liquibase ensures every environment (dev, test, prod) has the same schema. Migrations are version-controlled and auditable.

### Concept: Multi-Database Approach
- `auth-service` manages its own `auth_db` schema (users, refresh_tokens)
- `ledger-service` manages its own `ledger_db` schema (accounts, transactions, journal_entries, idempotency_keys)
- Each service has a `db/changelog/db.changelog-master.yaml` and includes specific changesets

### High-Level Approach
For each service:
1. Create `src/main/resources/db/changelog/db.changelog-master.yaml` pointing to include changesets
2. Create `src/main/resources/db/changelog/changes/001-*.yaml`, `002-*.yaml`, etc.
3. Configure `application.yml`:
   - `spring.datasource.url`: Point to the correct database (auth_db or ledger_db)
   - `spring.jpa.hibernate.ddl-auto: validate` (never create or update — Liquibase owns the schema)
   - `spring.liquibase.change-log: classpath:db/changelog/db.changelog-master.yaml`

When `docker-compose up` runs, Liquibase automatically applies all changesets to the correct database on startup.

✅ **Checkpoint:** Inspect PostgreSQL inside the Docker container. Both `auth_db` and `ledger_db` exist with all tables created by Liquibase. Databasechangelog table shows which migrations ran.

---

## Step 0.5: Verify Service-to-Service Communication

### What
Test that services can call each other through Eureka discovery

### Why
Microservices are only as good as their communication. If one service can't call another, the whole system fails.

### Concept: RestTemplate with Eureka Client
Services use `RestTemplate` with a load-balancer interceptor that resolves service names through Eureka. Instead of hardcoding `http://localhost:8082`, you request `http://ledger-service/api/accounts`.

### High-Level Approach
In any service that needs to call another:
1. Add `spring-cloud-starter-eureka-client` to dependencies
2. Add `@EnableDiscoveryClient` to main app class
3. Inject `RestTemplate` with a load-balancer interceptor
4. Call other services by name: `restTemplate.getForObject("http://ledger-service/api/accounts", ...)`
5. Eureka resolves `ledger-service` to its current address and port

Test it:
- Start all services
- Call API Gateway endpoint that routes to a service
- Inspect logs — should see successful call with service resolution

✅ **Checkpoint:** API Gateway can call Auth Service. Auth Service can call Ledger Service. No hardcoded URLs — all resolved through Eureka.

---

# 🔐 FEATURE 1: Auth Service (Spring Security + JWT)

## User Story
*"As an API consumer, I want to register with a username and password, log in to get a JWT token, and use that token to access protected ledger endpoints"*

## What You're Building
```
POST /auth/register   → Create user with BCrypt password, return JWT tokens
POST /auth/login      → Validate password, return JWT tokens
POST /auth/refresh    → Exchange refresh token for new access token
POST /auth/logout     → Invalidate refresh token
GET  /auth/validate   → Called by Gateway to validate token
```

## Architecture for This Feature
```
Client → API Gateway → Auth Service → auth_db
                           ↓
                    Spring Security + JWT Token Provider
```

---

## Step 1.1: Design the User Entity and Database Schema

### What
Define the User JPA entity and create Liquibase migrations for users and refresh_tokens tables

### Why
Authentication starts with storing and validating user credentials. The schema must support password hashing, token tracking, and fast lookups by username.

### Concept: Password Hashing with BCrypt
BCrypt is a deliberately slow hashing algorithm (takes ~100ms per hash). This prevents brute-force attacks: an attacker trying 1 million passwords would take 100,000 seconds. Never store plain passwords.

### Concept: Refresh Token Pattern
JWT access tokens are short-lived (24 hours) for security. When they expire, clients use a refresh token (7 days) to get a new access token without re-entering their password. Refresh tokens are stored in the database so they can be revoked (logout).

### High-Level Approach
Create Liquibase changeset `001-create-users.yaml` with:
- `users` table: id (UUID), username (VARCHAR UNIQUE), email, password_hash (VARCHAR — BCrypt output), roles (JSON), is_active, created_at, updated_at
- `refresh_tokens` table: id (UUID), user_id (FK), token (VARCHAR UNIQUE), expires_at, created_at
- Indexes on username and email for fast lookups

Create User entity with:
- No plain password field — only password_hash
- Roles field as a collection (stored as JSON in PostgreSQL)
- is_active flag to soft-delete users without data loss
- Proper getters/setters with no sensitive data leaks

✅ **Checkpoint:** Run auth-service on port 8081. Liquibase creates users and refresh_tokens tables. No errors in logs.

---

## Step 1.2: Implement Spring Security Configuration

### What
Configure Spring Security to enable servlet security, define which endpoints are public vs protected, and set up BCrypt password encoding

### Why
Spring Security is the standard for securing Java web applications. It manages authentication (who are you?), authorization (what can you do?), and filters all requests through security layers.

### Concept: SecurityFilterChain
Spring Security applies a chain of filters to every HTTP request:
1. JWT Authentication Filter (validate token)
2. Authorization Filter (check roles)
3. CSRF/CORS filters

You configure which URLs bypass these filters (public: /auth/register, /auth/login) and which require them (protected: /api/*).

### High-Level Approach
Create a SecurityConfig class with:
- `@Bean SecurityFilterChain` that configures HttpSecurity
- Permit all requests to /auth/register, /auth/login
- Require authentication for /api/*
- Disable CSRF (stateless JWT doesn't need CSRF protection)
- Add custom JwtAuthenticationFilter before the standard UsernamePasswordAuthenticationFilter
- `@Bean PasswordEncoder` returning BCryptPasswordEncoder

✅ **Checkpoint:** Call POST /auth/register without a token → succeeds. Call POST /api/accounts without a token → returns 401 Unauthorized.

---

## Step 1.3: Build the JWT Token Provider

### What
Create a service that generates, validates, and extracts claims from JWT tokens

### Why
JWT tokens are self-contained (no database lookup needed to validate them), signed (can't be forged), and carry claims (user ID, roles, expiry). The Token Provider is the single source of truth for token operations.

### Concept: JWT Structure
A JWT has three parts separated by dots: `header.payload.signature`
- Header: algorithm (HS256), token type (JWT)
- Payload: claims like sub (user ID), username, roles, iat (issued at), exp (expiration)
- Signature: HMAC-SHA256(header.payload, secret)

Signature ensures the token wasn't tampered with. If someone modifies the payload, the signature becomes invalid.

### High-Level Approach
Create JwtTokenProvider service with methods:
- `generateAccessToken(User)`: Create JWT with 24-hour expiry, user ID, username, roles, sign with secret
- `generateRefreshToken(User)`: Create JWT with 7-day expiry, only sub claim
- `validateToken(String)`: Check signature valid, not expired, no JwtException thrown
- `getUserIdFromToken(String)`: Extract sub claim and convert to UUID
- `getRolesFromToken(String)`: Extract roles claim as list of strings

Use JJWT library (io.jsonwebtoken:jjwt) which handles all JWT mechanics.

Secret should come from environment variable, never hardcoded.

✅ **Checkpoint:** Call generateAccessToken(user) → returns a JWT string. Copy the JWT, call validateToken(jwt) → returns true. Manually modify the JWT, call validateToken → throws JwtException.

---

## Step 1.4: Build the Authentication Controller and Service

### What
Create endpoints for register, login, refresh, logout. Wire them to Spring Security and the JWT Token Provider

### Why
These endpoints are the public API for authentication. They're the first thing clients call.

### Concept: Register vs Login
- **Register**: Client provides username, email, password → Server hashes password, creates User, returns tokens
- **Login**: Client provides username, password → Server validates password against hash, returns tokens
- **Refresh**: Client provides refresh token → Server validates it's still in DB and not expired, returns new access token
- **Logout**: Client provides refresh token → Server deletes it from DB, client can't refresh anymore

### High-Level Approach
Create AuthService with methods:
- `register(RegisterRequest)`: Validate username/email not taken, hash password, save User, generate tokens, save refresh token to DB, return AuthResponse
- `login(LoginRequest)`: Find User by username, validate password matches hash, generate tokens, save refresh token, return AuthResponse
- `refresh(RefreshTokenRequest)`: Find refresh token in DB, check not expired, generate new access token, return new AuthResponse
- `logout(LogoutRequest)`: Find refresh token in DB, delete it, return success

Create AuthController with REST endpoints:
- POST /auth/register, /auth/login, /auth/refresh (public, no auth required)
- POST /auth/logout (public, but client must provide their refresh token)
- GET /auth/validate (public, called by API Gateway to check if a JWT is valid)

✅ **Checkpoint:** POST /auth/register with new username → returns access_token and refresh_token. Use access_token in Authorization header on subsequent requests → accepted. Modify token → 401 Unauthorized.

---

# 🛡️ FEATURE 1.5: API Gateway (Request Routing & Cross-Cutting Concerns)

## User Story
*"As a system operator, I want a single entry point that validates requests, enforces authentication, tracks request flow, and routes to the correct microservice — so clients don't couple to internal service details"*

## What You're Building
```
Client → API Gateway (Port 8080)
    ├─ JWT Validation Filter (reject invalid tokens)
    ├─ Correlation ID Filter (inject X-Correlation-ID)
    ├─ Rate Limiting Filter (Redis-backed, per-user and per-IP)
    ├─ Request Transformation (extract user ID from JWT → X-User-Id header)
    ├─ CORS Headers (allow browser requests from allowed origins)
    ├─ Error Response Filter (consistent error format)
    └─ Route based on path → Auth/Ledger/Reporting services
```

## Why This Matters
Without the gateway as middleware:
- Every service duplicates JWT validation logic
- Clients hardcode service URLs (coupling breaks on deployment)
- No centralized rate limiting (spam hits all services)
- No traceability across services
- No consistent error response format

With the gateway:
- Auth logic enforced once at the edge
- Services trust headers set by gateway
- One place to add feature flags, request transformation, monitoring
- **This is what separates production systems from side projects**

---

## Step 1.5.1: Implement JWT Validation Filter

### What
A custom filter that extracts the JWT token from the Authorization header, validates it using the Auth Service's public key, and rejects invalid requests with 401 Unauthorized

### Why
Protect downstream services from invalid tokens. Services should never receive a request with an invalid JWT — the gateway should have rejected it.

### Concept: Token Validation
```
Client request: Authorization: Bearer <jwt_token>
Gateway:
  1. Extract token from header
  2. Validate signature (using Auth Service's JWT secret)
  3. Check expiration
  4. If invalid → return 401, don't route
  5. If valid → extract user ID and roles, continue
```

### High-Level Approach
Create `JwtValidationFilter` extending `OncePerRequestFilter`:
- Extract Authorization header
- Call Auth Service's `/auth/validate` endpoint (or validate locally with shared secret)
- On validation failure: return 401 with error response
- On success: extract claims (userId, roles), set as request attributes/headers
- For public endpoints (*/auth/**): skip validation entirely

Configure in `application.yml`:
- List public routes that bypass JWT validation
- Configure JWT secret (same as Auth Service)

✅ **Checkpoint:** Call GET /api/accounts without Authorization header → 401 Forbidden. Call with valid JWT → succeeds. Call with modified JWT → 401 Forbidden.

---

## Step 1.5.2: Add Correlation ID Propagation

### What
Inject a unique X-Correlation-ID into every request, and propagate it to all downstream service calls

### Why
**This is critical for production debugging.** When a transaction fails, you search logs by correlation ID and see the entire flow:
```
2026-06-21 10:15:23 [abc-123-def] Gateway: Request received
2026-06-21 10:15:23 [abc-123-def] Auth Service: Token validated
2026-06-21 10:15:24 [abc-123-def] Ledger Service: Transaction posted
2026-06-21 10:15:24 [abc-123-def] Kafka: Event published to transaction-settled
2026-06-21 10:15:25 [abc-123-def] Notification Service: Email sent
```

Without correlation ID, tracing across services is impossible.

### Concept: MDC (Mapped Diagnostic Context)
SLF4J's MDC is a thread-local map that your logging framework includes in every log line. Set it once, it appears in all downstream logs automatically.

### High-Level Approach
Create `CorrelationIdFilter`:
- Extract X-Correlation-ID header (if provided by client)
- If missing, generate UUID.randomUUID()
- Put into MDC: `MDC.put("correlationId", correlationId)`
- Add response header: `response.setHeader("X-Correlation-ID", correlationId)`
- Forward to downstream services via request header
- Clean up MDC in finally block: `MDC.clear()`

Update Logback config (logback-spring.xml):
- Change pattern to: `%d{ISO8601} [%X{correlationId}] %-5level %logger{36} - %msg%n`

Now every log line automatically includes the correlation ID.

✅ **Checkpoint:** Make a request without X-Correlation-ID. Check response headers — X-Correlation-ID was generated. Check gateway logs — correlation ID appears in every line.

---

## Step 1.5.3: Implement Rate Limiting

### What
A Redis-backed rate limiter that enforces limits:
- Per-user (authenticated users): 1000 requests/hour
- Per-IP (anonymous): 100 requests/hour
- Returns 429 Too Many Requests when exceeded

### Why
Protect the system from:
- Misbehaving clients hammering the API
- Accidental DOS from a bug
- Brute-force attacks on auth endpoints

### Concept: Sliding Window with Redis
```
Redis key: "rate-limit:user:{userId}:{hour}"
When request arrives:
  1. Get current count from Redis
  2. If count >= limit → return 429
  3. Increment counter
  4. Set expiry to 1 hour
```

### High-Level Approach
Create `RateLimitingFilter`:
- Extract user ID from JWT (or IP address for anonymous)
- Create Redis key: `rate-limit:{userId}:{currentHour}`
- Call Redis: `increment key, expire key after 1 hour`
- If value > threshold → return 429 with Retry-After header
- If within limit → continue to downstream

Configure in properties:
```
app.rate-limit.authenticated: 1000    # per hour
app.rate-limit.anonymous: 100         # per hour
```

Use Spring Data Redis for easy integration.

✅ **Checkpoint:** Make 101 unauthenticated requests from same IP → 101st returns 429. Make 1001 authenticated requests as same user → 1001st returns 429. Wait 1 hour (or mock time), limits reset.

---

## Step 1.5.4: Request Transformation & Header Injection

### What
Extract claims from validated JWT and inject them as headers that downstream services expect

### Why
Services need to know who made the request without parsing JWT themselves. The gateway does this work once.

### High-Level Approach
After JWT validation, inject headers:
- `X-User-Id`: UUID of authenticated user (from JWT sub claim)
- `X-User-Roles`: Comma-separated roles (from JWT roles claim)
- `X-Request-Timestamp`: When gateway received the request

Downstream services read these headers and trust them (because gateway validated).

Example in Feature 2 (Ledger Service):
```java
// Service can now extract user context without parsing JWT
String userId = request.getHeader("X-User-Id");
String roles = request.getHeader("X-User-Roles");
// Use for audit logging, access control, etc.
```

✅ **Checkpoint:** Make request with valid JWT containing userId and roles. Inspect request headers in ledger-service logs — X-User-Id and X-User-Roles are present.

---

## Step 1.5.5: Add CORS Support

### What
Configure Cross-Origin Resource Sharing so browser-based clients can make requests

### Why
If your clients are web apps running on a different origin (e.g., frontend.example.com calling api.example.com), browsers enforce CORS. Without proper headers, the browser blocks the response even if the server returns 200.

### High-Level Approach
Create `CorsConfig` with Spring's `@Bean WebMvcConfigurer`:
```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                    .allowedOrigins("http://localhost:3000")  // frontend dev server
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .exposedHeaders("X-Correlation-ID")
                    .allowCredentials(true)
                    .maxAge(3600);
            }
        };
    }
}
```

In production, replace `localhost:3000` with your actual frontend domain.

✅ **Checkpoint:** Browser client at localhost:3000 makes fetch request to gateway:8080 → succeeds (CORS headers are correct). Unmapped origin → request rejected by browser.

---

## Step 1.5.6: Consistent Error Response Format

### What
Ensure all errors (from gateway filters, routing failures, invalid input) return the same JSON structure

### Why
Clients can parse error responses consistently. Without this, one error is `{"error": "..."}`, another is `{"message": "..."}`, another is plain text.

### High-Level Approach
Create global `GlobalExceptionHandler` (via `@ControllerAdvice`):
```json
{
  "timestamp": "2026-06-21T10:15:23Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired JWT token",
  "path": "/api/accounts",
  "correlationId": "abc-123-def"
}
```

Handle:
- 401 Unauthorized (JWT validation failed)
- 403 Forbidden (valid JWT but insufficient roles)
- 429 Too Many Requests (rate limit exceeded)
- 404 Not Found (route not found)
- 5xx (downstream service unavailable)

✅ **Checkpoint:** Call /api/accounts without JWT → 401 with standard format. Call /invalid-endpoint → 404 with standard format. Hit rate limit → 429 with standard format.

---

## Checkpoint: Gateway Feature Complete
- ✅ JWT validation on every request
- ✅ Correlation ID flows through all logs
- ✅ Rate limiting protects from abuse
- ✅ Headers inject user context
- ✅ CORS allows browser clients
- ✅ Error responses are consistent
- ✅ Services receive trusted headers and correlation ID

After this feature, downstream services can assume:
- Request has valid JWT (or is public)
- Request has X-Correlation-ID in logs and headers
- Request has X-User-Id for audit logging
- Rate limiting is enforced at edge

---

# 💳 FEATURE 2: Ledger Service (Accounts & Core Logic)

## User Story
*"As an API consumer, I want to create accounts and query their balances so I have the foundation for all money movements"*

## What You're Building
```
POST /api/accounts               → Create a new account
GET  /api/accounts/{id}          → Get account details
GET  /api/accounts/{id}/balance  → Get current balance
```

## Architecture for This Feature
```
API Gateway → Ledger Service
    → Extract X-User-Id header (set by Gateway after JWT validation)
    → AccountService → AccountRepository → ledger_db
```

---

## Step 2.1: Design the Account Entity

### What
Define the Account JPA entity that models a financial account

### Why
Accounts are the foundation of the ledger. Every transaction references two accounts. Getting this model right ensures consistency throughout the system.

### Concept: Account Types
In accounting, accounts are classified:
- **ASSET**: Cash, bank accounts (debits increase, credits decrease)
- **LIABILITY**: Loans, payables (credits increase, debits decrease)
- **INCOME**: Revenue (credits increase, debits decrease)
- **EXPENSE**: Costs (debits increase, credits decrease)

Different account types have different rules. For this ledger, we'll treat all accounts as ASSET type initially, but the enum supports others for future expansion.

### Concept: Optimistic Locking with @Version
When two concurrent requests try to update the same account, we use `@Version` to detect conflicts. JPA increments the version on every update. If the second request's version doesn't match what's in the database, an OptimisticLockException is thrown, signaling a conflict. The service can retry.

### High-Level Approach
Create Liquibase changeset `001-create-accounts.yaml` for auth-service, and `001-create-accounts.yaml` for ledger-service with:
- accounts table: id (UUID PK), name (VARCHAR), type (VARCHAR/ENUM), currency (VARCHAR(3)), is_active (BOOLEAN), version (BIGINT), created_at, updated_at

Create Account entity with:
- @Id private UUID id (not auto-increment — use UUID.randomUUID() before save)
- @Version private Long version (JPA handles incrementing)
- Enumerated type field for account type
- is_active flag (never delete accounts, only mark inactive)
- created_at timestamp auto-set on insert
- No balance field — balance is always derived from journal entries

✅ **Checkpoint:** Start ledger-service. Liquibase creates accounts table. POST /api/accounts with valid data creates an account. GET /api/accounts/{id} returns the account.

---

## Step 2.2: Build the Accounts Controller and Service

### What
Create REST endpoints to create and retrieve accounts

### Why
This is your first end-to-end slice: HTTP request → Controller → Service → Repository → Database → response.

### Concept: DTOs vs Entities
Never return JPA @Entity objects directly to API callers. Create separate DTO classes:
- CreateAccountRequest: username, password, amount (what the client sends)
- AccountResponse: id, username, balance, createdAt (what the API returns)

This decouples the API contract from the database schema. If you add columns to the Account entity, the API response doesn't change.

### Concept: Service Layer
The controller validates the request format (@Valid), then hands it to the service. The service implements business rules (e.g., no duplicate account names). The repository handles all database access.

### High-Level Approach
Create AccountService with methods:
- `createAccount(CreateAccountRequest)`: Validate no duplicate name for this currency, generate UUID, hash password if storing user creds, create Account entity, save to DB, return AccountResponse
- `getAccount(UUID accountId)`: Find account by ID, return AccountResponse
- `listAccounts(userId)`: Find all accounts for a user (if tracking ownership), return list

Create AccountController with endpoints:
- POST /api/accounts — create account
- GET /api/accounts/{id} — get single account
- GET /api/accounts — list accounts (paginated)

Use constructor injection for AccountRepository, not @Autowired field injection.

✅ **Checkpoint:** Create 3 accounts via POST. List them via GET. Query each individually. All data matches what was sent.

---

## Step 2.3: Build the Balance Endpoint

### What
Create GET /api/accounts/{id}/balance that returns the current balance by summing journal entries

### Why
The balance is the most critical query in the system. It must always be computed fresh from journal entries, never from a denormalized field. This ensures correctness.

### Concept: Derived Balance
Balance is computed from journal entries using the formula: `SUM(credits) - SUM(debits)`. This means you can reconstruct the balance at any point in history by filtering entries by date. The journal is the source of truth.

### Concept: Query Optimization
To avoid scanning all journal entries for every balance query, add indexes on (account_id, entry_type) in Liquibase. This makes balance calculations O(log n) instead of O(n).

### High-Level Approach
Create JournalEntryRepository with a native query or @Query:
- `sumByAccountAndType(UUID accountId, EntryType type)`: Sum all amounts for this account where entry_type matches, return BigDecimal

Create AccountService method:
- `getBalance(UUID accountId)`: Query sum of CREDIT entries, query sum of DEBIT entries, return balance = credits - debits

Return a BalanceResponse DTO with: accountId, balance, currency, asOf (current timestamp)

✅ **Checkpoint:** Create 2 accounts. Query both balances — both return 0. No journal entries yet, so balances are zero. This is correct.

---

# 💸 FEATURE 3: Double-Entry Transactions

## User Story
*"As an API consumer, I want to post a transfer between two accounts so that money moves atomically and the books always balance"*

## What You're Building
```
POST /api/transactions              → Post a double-entry transfer
GET  /api/transactions/{id}         → Get transaction details
GET  /api/accounts/{id}/entries     → List journal entries for an account
```

## Architecture for This Feature
```
POST /api/transactions
    ↓
TransactionService.postTransfer() — annotated @Transactional
    ↓
Within a single DB transaction:
  1. Insert Transaction record (PENDING)
  2. Insert JournalEntry (DEBIT on source)
  3. Insert JournalEntry (CREDIT on destination)
  4. Update Transaction status → SETTLED
    ↓
Both entries commit or both roll back
```

---

## Step 3.1: Design the Transaction and JournalEntry Entities

### What
Create two new entities: Transaction (the group) and JournalEntry (the individual debit/credit line)

### Why
Every money movement needs both:
- Transaction: metadata (who, when, why, status, settlement time)
- JournalEntries: the actual accounting records (which account, debit/credit, amount)

### Concept: Double-Entry Bookkeeping
Every transfer always creates exactly two journal entries:
```
Transfer ₹500 from Wallet A to Wallet B:
  JournalEntry 1: Account = Wallet A | Type = DEBIT  | Amount = 500
  JournalEntry 2: Account = Wallet B | Type = CREDIT | Amount = 500
```

Rule: SUM of debits must always equal SUM of credits. This prevents balance corruption.

### Concept: Journal Entry Immutability
Journal entries are **write-once**. Once created, they are never updated or deleted. A reversal doesn't edit the entry — it creates new counter-entries. This maintains an immutable audit trail.

### High-Level Approach
Create Liquibase changesets:
- `002-create-transactions.yaml`: transactions table with id, amount, currency, description, status (ENUM), idempotency_key (added in Feature 5), correlation_id, version, created_at, settled_at
- `003-create-journal-entries.yaml`: journal_entries table with id, transaction_id (FK), account_id (FK), entry_type (ENUM: DEBIT/CREDIT), amount, version, created_at
- Add indexes: (transaction_id) on journal_entries, (account_id) on journal_entries for fast lookups

Create entities with proper relationships:
- Transaction @OneToMany JournalEntry with cascade
- JournalEntry @ManyToOne Account and @ManyToOne Transaction
- No updatedAt on JournalEntry — immutable

✅ **Checkpoint:** Liquibase creates both tables. No compile errors with new entities. Table structure verified in PostgreSQL.

---

## Step 3.2: Build the Transaction Posting Logic

### What
Implement TransactionService.postTransfer() — the core bookkeeping engine

### Why
This is where correctness matters most. A bug here means balances go wrong — money is lost or created from nowhere. This method is critical to get right.

### Concept: @Transactional Atomicity
Spring's @Transactional annotation wraps the entire method in a single PostgreSQL transaction. If any step throws an exception, Spring automatically rolls back everything. The accounts never end up in a partially-updated state.

### Concept: Optimistic Locking Retry
When two requests update the same account concurrently:
1. Both read account (version = 5)
2. Request A updates account, version becomes 6
3. Request B tries to update, but version is still 5 — mismatch! OptimisticLockException
4. Service catches the exception, retries the entire transaction
5. On retry, Request B reads account with version 6, updates succeed

This prevents lost updates without using database locks (which cause deadlocks).

### High-Level Approach
Create TransactionService with postTransfer() method:
- Validate both accounts exist and are active
- Validate amount > 0
- Validate sufficient balance on source account
- Create Transaction record with status PENDING
- Create DEBIT JournalEntry on source account
- Create CREDIT JournalEntry on destination account
- Update Transaction status to SETTLED, set settledAt timestamp
- Return TransactionResponse DTO
- Wrap entire method with @Transactional

The @Transactional annotation ensures all database operations succeed together or all fail together.

✅ **Checkpoint:** Post a ₹500 transfer from Account A to Account B. Query both balances: A is -₹500, B is +₹500. Books balance (total debits = total credits). Query journal entries — 2 entries exist (DEBIT on A, CREDIT on B).

---

## Step 3.3: Write Unit and Integration Tests

### What
Create tests that verify transactions post correctly, balances update, and concurrent transfers don't corrupt data

### Why
The core correctness guarantee of the entire system depends on these tests. Unit tests with mocks are not sufficient here — you need to verify actual database behavior.

### Concept: @SpringBootTest + Testcontainers
Instead of mocking the database, use Testcontainers to spin up a real PostgreSQL container for tests. This catches subtle bugs that mocks miss (e.g., constraints, indexes, transaction isolation).

### High-Level Approach
Create TransactionServiceTest with test cases:
- Happy path: post valid transfer → both journal entries exist, balances correct
- Insufficient balance: post transfer with amount > balance → InsufficientBalanceException thrown
- Invalid accounts: post with non-existent source or destination → AccountNotFoundException thrown
- Concurrent transfers: two threads post simultaneously from the same account → both succeed, balance is correct (no lost update)
- Verify state: after posting, SUM(debits) = SUM(credits)

Create integration tests using Testcontainers:
- Spin up real PostgreSQL for each test
- Post real transactions
- Verify real database state
- Verify concurrency behavior

✅ **Checkpoint:** Run all tests — they pass. Test coverage for happy path + error paths. Concurrent tests verify no race conditions.

---

# 🔄 FEATURE 4: Transaction State Machine & Immutability

## User Story
*"As an API consumer, I want to reverse a settled transaction and have the system prevent invalid state transitions so the ledger is always consistent"*

## What You're Building
```
POST /api/transactions/{id}/reverse   → Reverse a settled transaction
```

## The State Machine
```
PENDING → PROCESSING → SETTLED → REVERSED
                    → FAILED
```

Invalid transitions (must be rejected):
- FAILED → SETTLED
- REVERSED → SETTLED
- PENDING → REVERSED

---

## Step 4.1: Enforce State Transitions

### What
Add a TransactionStateMachine validator that rejects any invalid state change

### Why
Without enforcement, a bug or bad API call can put a transaction into an impossible state (e.g. reversing a failed transaction). In a financial system, invalid states cause balance corruption.

### Concept: State Machine with EnumMap
Instead of a series of if-statements, use an EnumMap to define all valid transitions:
- From PENDING, you can go to PROCESSING
- From PROCESSING, you can go to SETTLED or FAILED
- From SETTLED, you can go to REVERSED
- From FAILED or REVERSED, nowhere (terminal states)

This is declarative and easy to verify: looking at one place tells you all valid transitions.

### High-Level Approach
Create TransactionStateMachine class with:
- Static EnumMap<TransactionStatus, Set<TransactionStatus>> defining valid transitions
- Method assertValidTransition(from, to) that checks the EnumMap and throws InvalidStateTransitionException if transition is invalid

Before any status update in TransactionService, call this validator.

✅ **Checkpoint:** Try to manually transition a transaction from FAILED to SETTLED via API → InvalidStateTransitionException thrown. Try to reverse a PENDING transaction → InvalidStateTransitionException. Try to reverse a SETTLED transaction → succeeds.

---

## Step 4.2: Build the Reversal Endpoint

### What
POST /api/transactions/{id}/reverse — reverses a settled transaction by creating new counter-entries

### Why
This is the immutability rule in action. You **do not** update the original journal entries. You create new entries that cancel them out. The original record stays intact forever.

### Concept: Reversal via Counter-Entries
Original Transaction (Settled):
```
JournalEntry: Account A | DEBIT  | ₹500
JournalEntry: Account B | CREDIT | ₹500
```

Reversal Transaction (new record):
```
JournalEntry: Account A | CREDIT | ₹500  ← cancels the original debit
JournalEntry: Account B | DEBIT  | ₹500  ← cancels the original credit
```

Net effect on both accounts: zero. Original entries: untouched, still in the database.

### High-Level Approach
Create TransactionService method reverseTransaction(UUID transactionId):
- Find original transaction, verify status is SETTLED
- Validate transition SETTLED → REVERSED (use state machine)
- Create new Transaction record with reversalOfId FK to original
- Query the original transaction's journal entries
- Create mirror JournalEntries (flip DEBIT ↔ CREDIT)
- Mark original transaction status as REVERSED
- Wrap in @Transactional

✅ **Checkpoint:** Post a ₹500 transfer. Both balances updated. Reverse it. Both balances return to zero. Query journal entries — 4 entries exist (2 original + 2 reversal). None were modified. Original still exists with status SETTLED, reversal marked as REVERSED.

---

# 🛡️ FEATURE 5: Idempotency Protection

## User Story
*"As an API consumer, if my network drops after sending a payment request and I retry it, I want to be guaranteed that the payment happens exactly once — not twice"*

## What You're Building
```
Every write endpoint accepts: Idempotency-Key header
Duplicate request → returns the original response (not an error, not a new transaction)
```

## Why This Is Hard
```
Timeline of the bug without idempotency:

T=0ms:  Client sends POST /transactions (₹500 transfer)
T=50ms: Server processes it, writes to DB
T=51ms: Network drops — client never receives the 200 response
T=52ms: Client assumes failure, retries POST /transactions (same data)
T=52ms: Server processes it again, writes to DB
Result: ₹1000 was transferred. Client only intended ₹500. DISASTER.

With idempotency:
T=0ms:  First request → server processes, stores response, returns 200
T=52ms: Retry with same Idempotency-Key → server returns stored 200 response, NO NEW TRANSACTION
Result: ₹500 transferred exactly once.
```

---

## Step 5.1: Design the Idempotency Key Store

### What
A new idempotency_keys table that stores: the key, the response payload, HTTP status code, and when it expires

### Why
You need to remember: "I already processed this request, here is the response I gave." On duplicate requests, replay that response.

### Concept: Idempotency Key Lifecycle
1. Request arrives with Idempotency-Key: abc-123
2. Check DB: does key abc-123 exist?
   - YES → return the stored response (no processing)
   - NO  → process the request, store key + response, return response
3. Key expires after 24 hours (safety net for cleanup)

### High-Level Approach
Create Liquibase changeset `004-create-idempotency-keys.yaml` for ledger-service with:
- idempotency_keys table: key (VARCHAR PK), response_payload (TEXT — serialized JSON response), http_status_code (INT), created_at, expires_at
- Index on expires_at for cleanup queries

Create IdempotencyKey entity mapping this table.
Create IdempotencyKeyRepository with methods:
- findByKey(String key): Optional<IdempotencyKey>
- save(IdempotencyKey): persists or updates
- deleteExpiredBefore(LocalDateTime): cleanup old keys

✅ **Checkpoint:** Send a request with Idempotency-Key: test-123. Inspect database — row exists in idempotency_keys with the response payload. Send duplicate request — database shows no new row created.

---

## Step 5.2: Implement the Race Condition Guard

### What
Protect against two identical requests arriving at the same millisecond — both checking the DB, both finding no key, both processing

### Why
A PRIMARY KEY constraint alone does not fully protect you. Two requests can both read "key doesn't exist" before either has written it. The second write fails with constraint violation — you need to handle that gracefully.

### Concept: INSERT ... ON CONFLICT (PostgreSQL)
PostgreSQL's `INSERT ... ON CONFLICT DO NOTHING` handles this race condition:
- Request A: INSERT idempotency_key (affected rows = 1)
- Request B (same millisecond): INSERT idempotency_key (affected rows = 0, conflict on unique key)
- Request B then reads the existing row and returns the stored response

This is atomic — PostgreSQL guarantees exactly one write succeeds, the other gets 0 rows affected.

### High-Level Approach
Create IdempotencyKeyRepository with native query:
- insertIfAbsent(key, payload, statusCode, expiresAt): INSERT ... ON CONFLICT DO NOTHING, returns number of rows affected
- If 0 rows affected, key already exists — read the existing row and return its stored response
- If 1 row affected, key was new — continue to process the request

Create an idempotency filter or aspect that:
1. Extracts Idempotency-Key header from request
2. Calls insertIfAbsent() — if 0 rows, return stored response immediately
3. If 1 row, process the request normally
4. After response is generated, serialize it and store in the idempotency_keys row

✅ **Checkpoint:** Send 10 identical POST /transactions requests simultaneously with the same idempotency key. Exactly 1 transaction exists in the database. All 10 responses are identical. Database shows 1 idempotency key row.

---

# 📨 FEATURE 6: Async Events (Kafka)

## User Story
*"As a system operator, I want transaction settlement events to be published asynchronously so downstream systems (notifications, analytics) are decoupled from the core ledger"*

## What You're Building
```
Transaction Settles in Ledger Service
      ↓
Publish TransactionSettledEvent → Kafka topic: transaction-settled
      ↓
Notification Service @KafkaListener consumes event
      ↓
Logs settlement with correlation ID (simulates sending email/alert)
      ↓
On repeated failure → Spring routes to Dead Letter Topic (DLT)
```

## Architecture for This Feature
```
Ledger Service                     Notification Service
     │                                     │
     │  TransactionSettledEvent            │
     └── topic: transaction-settled ───────┤
                   │                   @KafkaListener
          (3 retries max)          processes event
                   │                   │
        topic: transaction-       logs settlement
         settled.DLT             sends notifications
       (failed events)
```

---

## Step 6.1: Configure Kafka Topics

### What
Define the main topic and Dead Letter Topic (DLT) in Spring Boot configuration

### Why
Unlike message queues where you create queue resources explicitly, Kafka topics can be auto-created or defined as Spring beans. The DLT is a naming convention — Spring Kafka creates it automatically when you configure retry + error handling.

### Concept: Dead Letter Topic
```
Normal flow:   Event → topic → Consumer → Success → offset committed
Failed flow:   Event → topic → Consumer → Failure → Retry 1 → Retry 2 → Retry 3
                                                                               ↓
                                                            topic.DLT
```

Events in the DLT are preserved for later inspection and replay. You can use tools like Kafdrop to view them.

### High-Level Approach
In ledger-service, create a KafkaTopicConfig class with:
- @Bean NewTopic for transaction-settled topic (3 partitions, 1 replica for dev)
- Spring Kafka auto-creates the DLT when error handling is configured

Configure application.yml for Kafka:
- bootstrap-servers: kafka:9092
- producer settings (acks: all for safety, retries: 3)
- consumer settings (group-id: notification-service, auto-offset-reset: earliest)

✅ **Checkpoint:** Start ledger-service and notification-service. Use kafka-console-topics or Kafdrop to list topics — transaction-settled exists. After first message failure in consumer, transaction-settled.DLT is created.

---

## Step 6.2: Publish Events on Settlement

### What
After a transaction settles, publish a TransactionSettledEvent to the Kafka topic

### Why
The ledger API should not directly call downstream systems (email, notifications, analytics). Publishing an event means downstream systems can be added, removed, or changed without touching the core ledger. This is the decoupling principle.

### Concept: KafkaTemplate
KafkaTemplate is Spring's way of sending messages to Kafka. You provide the topic name, an optional key (for partitioning), and the event payload. Spring handles serialization.

### Concept: Message Keys for Ordering
Use the transaction ID as the Kafka message key. This ensures all events for one transaction go to the same partition, preserving order. If you don't provide a key, messages might be distributed across partitions, potentially causing out-of-order processing.

### High-Level Approach
Create TransactionEventPublisher service:
- Inject KafkaTemplate<String, TransactionSettledEvent>
- Method publishSettlement(Transaction): create event object, send to Kafka with transaction ID as key, log published event
- Call this method at the END of postTransfer(), after the @Transactional block completes

Important: Call Kafka publish AFTER the database transaction commits. If Kafka send fails, don't roll back the ledger — the event can be replayed from Kafka later.

✅ **Checkpoint:** Post a transaction. Within 2 seconds, the Notification Service logs the settlement event. Check Kafka with kafka-console-consumer — event appears in transaction-settled topic.

---

## Step 6.3: Build the Settlement Consumer

### What
A @KafkaListener in Notification Service that consumes settlement events

### Why
The consumer runs independently from the API. It can be scaled separately, restarted without affecting the API, and processes events at its own pace. This is the power of async decoupling.

### Concept: @KafkaListener with Retry + DLT
Spring Kafka automatically retries failed listeners:
1. Consumer processes event
2. If exception thrown → Spring retries (configurable count, backoff)
3. If all retries fail → Spring publishes to DLT
4. @DltHandler method processes DLT messages separately (for alerting, manual inspection)

### High-Level Approach
Create SettlementConsumer in notification-service:
- @KafkaListener on topic transaction-settled, groupId notification-service
- Method consume(TransactionSettledEvent): log with correlationId, call downstream service (email service, for now just log)
- If exception thrown, Spring retries (up to 3 times, 1 second apart)
- @DltHandler method handleDlt(TransactionSettledEvent): log error, alert ops team

Configure error handling in KafkaConfig:
- DefaultErrorHandler with DeadLetterPublishingRecoverer (routes failed messages to DLT)
- FixedBackOff(1000L, 3) for 3 retries, 1 second apart

✅ **Checkpoint:** Post a transaction. Notification Service consumes event and logs it with correct correlationId. Stop Notification Service mid-processing of an event (simulate failure) — message goes to DLT. Inspect DLT with kafka-console-consumer.

---

# 🔍 FEATURE 7: Reconciliation Engine

## User Story
*"As a system operator, I want to run a reconciliation check for any time window and get a report showing whether the books balance — and if not, exactly which accounts have discrepancies"*

## What You're Building
```
GET /api/reconciliation/report?from=2025-01-01&to=2025-01-31
    → { balanced: true, totalDebits: 50000, totalCredits: 50000, differenceAmount: 0 }

GET /api/reconciliation/discrepancies?from=...&to=...
    → [{ accountId, expectedBalance, difference }, ...]
```

---

## Step 7.1: Build the Reconciliation Report

### What
An endpoint that sums all debit journal entries and all credit journal entries for a time window and verifies they are equal

### Why
In a correct double-entry system, for any complete set of transactions, debits always equal credits. If this check ever fails, there is a bug or data corruption. Real fintech companies run this check every single day.

### Concept: The Fundamental Rule
For any time window where all transactions are complete (status SETTLED or REVERSED):
```
SUM of all DEBIT entries  =  SUM of all CREDIT entries

If this is not true → something is very wrong → immediate investigation required
```

### High-Level Approach
Create ReconciliationService with method reconciliationReport(from, to):
- Query: sum all journal entries with entry_type = DEBIT in time window where transaction.status IN (SETTLED, REVERSED)
- Query: sum all journal entries with entry_type = CREDIT in same window
- Calculate: difference = sum_debits - sum_credits
- Return: ReconciliationReport { balanced: (difference == 0), totalDebits, totalCredits, difference, transactionCount, from, to }

Create ReconciliationController endpoint:
- GET /api/reconciliation/report?from=...&to=...
- Accept LocalDate parameters, convert to LocalDateTime (start of day, end of day)
- Return ReconciliationReportResponse

✅ **Checkpoint:** Post several transfers and reversals. Run reconciliation report for that time window. Result: balanced = true, totalDebits = totalCredits. Manually corrupt a journal entry in the database. Run reconciliation — balanced = false, difference shows the corruption amount.

---

## Step 7.2: Build the Discrepancy Detector

### What
For each account, compare what the balance *should be* (computed fresh from journal entries) against any stored balance

### Why
If you ever add a stored balance field (e.g. for performance) it can drift from the true computed balance due to bugs or missed updates. This report surfaces that drift. It's an internal consistency check.

### High-Level Approach
Create ReconciliationService method discrepancies(from, to):
- For each account in the database:
  - Compute expectedBalance = SUM(credits) - SUM(debits) from journal entries in the time window
  - Compare against the balance in Redis cache (if caching is implemented)
  - If difference > 0, add to discrepancies list with the difference amount
- Return list of accounts with discrepancies

Create ReconciliationController endpoint:
- GET /api/reconciliation/discrepancies?from=...&to=...
- Return list of DiscrepancyResponse { accountId, accountName, expectedBalance, cachedBalance, difference }

✅ **Checkpoint:** Post transfers. Run discrepancies report — all accounts balanced, list is empty. Manually corrupt a balance value in Redis. Run discrepancies — that account appears with the exact difference. Fix it. Run again — list empty again.

---

# ⚡ FEATURE 8: Performance — Redis Caching

## User Story
*"As an API consumer, I want balance queries to respond in under 50ms even under high concurrent load"*

## What You're Building
```
GET /api/accounts/{id}/balance
  → Check Redis first (cache hit: ~2ms)
  → If miss: compute from PostgreSQL, store in Redis, return (~40ms)
  → On any journal entry: invalidate the cache for that account
```

---

## Step 8.1: Add Redis Cache-Aside for Balances

### What
Cache the computed balance in Redis on first read, serve from cache on subsequent reads

### Why
Computing a balance requires summing all journal entries for an account. At high volume (thousands of entries per account), this query gets slow. Redis caches the result, making subsequent reads instant.

### Concept: Cache-Aside Pattern
1. Request comes in for account balance
2. Check Redis for key `balance:{accountId}`
3. If hit: return cached value (2ms)
4. If miss: query PostgreSQL, store result in Redis with 5-minute TTL, return (40ms)
5. On write (new journal entry): delete key `balance:{accountId}` from Redis

### Concept: Spring Cache Abstraction
Spring provides @Cacheable and @CacheEvict annotations that handle cache logic transparently. You don't write any Redis code yourself.

### High-Level Approach
In AccountService:
- Add @Cacheable(value = "balances", key = "#accountId") to getBalance() method
  - Spring checks Redis before calling the method
  - On miss, executes the method and stores result in Redis with default TTL
  - On subsequent calls, returns cached value without executing the method

- After every postTransfer() call that creates journal entries, call @CacheEvict(value = "balances", key = "#accountId") for both source and destination accounts
  - Spring removes the key from Redis
  - Next getBalance call will miss cache and recompute

Configure application.yml:
- spring.cache.type: redis
- spring.data.redis.host: localhost
- spring.data.redis.port: 6379
- spring.cache.redis.time-to-live: 300000 (5 minutes as safety net)

✅ **Checkpoint:** Query balance 10 times → first is slow (~40ms), rest are fast (~2ms, cache hits). Post a transfer → caches for both accounts invalidated. Query balances again → first query is slow, rest are fast. Inspect Redis with redis-cli, see `balance:{id}` keys.

---

## Step 8.2: Verify Correctness Under Load

### What
Confirm that the cache serves correct values and invalidates correctly after transfers

### Why
Caching introduces complexity: if invalidation misses one cache key, the next query serves a stale balance. You need to verify no scenario exists where the cache is wrong.

### High-Level Approach
Create load test scenarios:
- 100 concurrent balance read requests on same account → verify all get same value
- Post a transfer → verify both account caches invalidated
- Query both balances → verify fresh values computed and re-cached
- Verify: no scenario where cache serves a balance that doesn't match SUM of journal entries

Use JMeter or load testing framework to hammer the API with concurrent requests.

✅ **Checkpoint:** Under 100 concurrent balance read requests, P95 latency < 50ms. Posting a transaction correctly invalidates both account caches. Cache always returns the same value as a direct DB computation.

---

# 🔭 FEATURE 9: Observability & Polish

## User Story
*"As a developer or operator, I want to trace the full lifecycle of any transaction across all logs and events using a single ID, and I want the project to be easy to set up and understand"*

## What You're Building
```
Every request → assigned a Correlation-ID
Every log line → includes Correlation-ID, account IDs, amounts (via MDC)
Every Kafka event → includes Correlation-ID
Result: search logs by one Correlation-ID → full picture of transaction across all services
```

---

## Step 9.1: Add Correlation ID Filter

### What
An HTTP filter that assigns an X-Correlation-ID to every incoming request and propagates it through all downstream operations

### Why
When something goes wrong in production, you need to trace one transaction's journey across the API Gateway, Auth Service, Ledger Service, Kafka, and Notification Service. Without a shared ID, this is nearly impossible.

### Concept: MDC (Mapped Diagnostic Context)
SLF4J's MDC is a thread-local map that Logback automatically includes in every log line. Set a value in MDC at the start of the request, and every log line from that request includes that value.

### High-Level Approach
In API Gateway, create CorrelationIdFilter:
- Extends OncePerRequestFilter
- In doFilterInternal: extract X-Correlation-ID header (or generate UUID if missing)
- Put into MDC: MDC.put("correlationId", correlationId)
- Add to response header: response.setHeader("X-Correlation-ID", correlationId)
- Call chain.doFilter
- In finally block: MDC.clear() (critical — prevents thread pool leaks)

Configure Logback pattern in logback-spring.xml:
- Change pattern to include [%X{correlationId}]: `%d{ISO8601} [%X{correlationId}] %-5level %logger{36} - %msg%n`

Now every log line automatically includes the correlation ID.

✅ **Checkpoint:** Make a request without X-Correlation-ID header. Check response headers — X-Correlation-ID was generated and included. Check logs — all lines include [correlation-id] in brackets.

---

## Step 9.2: Structured Logging and Observability

### What
Configure structured JSON logging and add health checks for all dependencies

### Why
Structured logs (JSON) can be searched, filtered, and aggregated. In production, you'd ship these to a log aggregation service (Datadog, Splunk, Loki). Health checks let infrastructure monitors know if services are alive.

### High-Level Approach
Add logstash-logback-encoder to pom.xml. Configure logback-spring.xml with JSON appender that outputs:
- timestamp
- level
- logger name
- message
- MDC (includes correlationId)
- stacktrace (on errors)

Add key events to be logged in JSON at minimum:
- Transaction posted: { correlationId, transactionId, amount, currency, fromAccount, toAccount }
- State transition: { correlationId, transactionId, fromStatus, toStatus }
- Cache hit/miss: { correlationId, accountId, cacheHit: true/false }
- Kafka event published: { correlationId, transactionId, topic }
- Kafka event consumed: { correlationId, transactionId, status }

Configure Spring Actuator for health checks:
- GET /actuator/health returns status of PostgreSQL, Redis, Kafka
- GET /actuator/metrics shows request counts, latencies, database connections
- Infrastructure monitors these endpoints

✅ **Checkpoint:** Make a request. Inspect logs — all lines are JSON formatted with correlationId. Check /actuator/health — shows UP for database, redis, kafka. Check /actuator/metrics — shows request counts and latencies.

---

## Step 9.3: API Documentation with Swagger

### What
Configure Springdoc OpenAPI to generate Swagger documentation for all REST endpoints

### Why
Swagger provides an interactive UI where API consumers can see all endpoints, parameters, and response schemas. It's generated automatically from your controller code.

### High-Level Approach
Add springdoc-openapi-starter-webmvc-ui to pom.xml. Annotate controllers and methods:
- @RestController with @Tag(name = "Accounts")
- @PostMapping with @Operation(summary = "Create an account")
- @RequestBody with @io.swagger.v3.oas.annotations.parameters.RequestBody
- @ApiResponse(responseCode = "201", description = "Account created")

Swagger UI is automatically available at:
- http://localhost:8080/swagger-ui.html

✅ **Checkpoint:** Visit Swagger UI. See all endpoints documented with request/response schemas. Try executing a request from the UI — it works.

---

## Step 9.4: Docker Compose Final Configuration

### What
Ensure docker-compose.yml is production-ready and all services start cleanly

### Why
Your final deliverable is "docker-compose up" starting everything. It should be reproducible on any machine.

### High-Level Approach
docker-compose.yml should:
- Define all 6 services in correct dependency order
- Eureka starts first (no external dependencies)
- Auth Service and Ledger Service depend on PostgreSQL and Eureka
- Gateway depends on all services
- Health checks on each service (optional but recommended)
- Environment variables properly configured for each service
- Volumes for persistent PostgreSQL data

Dockerfile for each service should:
- Use official OpenJDK 21 image as base
- Copy pom.xml and src, run mvn clean package
- Expose the correct port
- Set environment variables for Eureka and database URLs

✅ **Checkpoint:** Run docker-compose up --build from a clean state (no containers, no volumes). All 6 services start. Visit Eureka dashboard — all services registered. Visit API Gateway — endpoints work. Visit Swagger UI on gateway:8080/swagger-ui.html.

---

## Step 9.5: GitHub Actions CI + Testing

### What
Set up GitHub Actions workflow that runs tests on every commit

### Why
Continuous Integration catches bugs early. Tests run automatically, you're notified if something breaks.

### High-Level Approach
Create .github/workflows/ci.yml that:
- Triggers on push to main and pull requests
- Uses services: PostgreSQL (for tests), Kafka (for tests)
- Runs mvn clean test
- Optionally runs code coverage report

Ensure tests use Testcontainers for PostgreSQL and Kafka, so they work in CI without manual setup.

✅ **Checkpoint:** Push code to GitHub. GitHub Actions workflow runs. All tests pass (or fail with clear error message). Workflow status shown in PR.

---

## Step 9.6: Final Polish

### What
README, architecture diagrams, API examples, setup instructions

### Why
First impression matters. A well-documented project demonstrates professionalism and makes it easy for interviewers to understand what you built.

### High-Level Approach
Create comprehensive README with:
- High-level architecture diagram
- What problem it solves (financial transactions need accuracy, atomicity, auditability)
- Quick start: "docker-compose up then curl examples"
- Features implemented (Phase 0-9)
- Testing instructions
- Design decisions (why Kafka, why microservices, why double-entry)
- Interview talking points

Add example curl/Postman requests:
- Register, login, get JWT
- Create accounts
- Post transfers
- Reverse transactions
- Check balances

✅ **Final Checkpoint:** Clone repo on a fresh machine. Follow README. Run docker-compose up. All services start. Run tests. Call API endpoints. Everything works.

---

# 🔄 FEATURE 10: Resilience & Error Handling (Circuit Breakers, Retries)

## User Story
*"As a system operator, if a downstream service (Ledger, Auth) becomes temporarily unavailable, I want requests to fail gracefully instead of timing out — and I want the system to recover automatically when the service comes back online"*

## What You're Building
```
Request → Gateway → Ledger Service (temporarily down)
  ├─ First attempt: timeout after 5 seconds → retry
  ├─ Retry 1: timeout → retry
  ├─ Retry 2: timeout → open circuit breaker
  ├─ Circuit OPEN: reject new requests immediately (503 Service Unavailable)
  ├─ After 30 seconds: try HALF-OPEN (probe to see if service recovered)
  ├─ If probe succeeds: CLOSE circuit, resume normal routing
  └─ If probe fails: reopen circuit
```

## Why This Matters
Without resilience:
- One service down = cascading failures across system
- Timeouts block threads, exhausting connection pools
- Recovery is manual (someone restarts the service)

With resilience:
- Failures are isolated, don't cascade
- System degrades gracefully
- Automatic recovery when service comes back online
- Threads don't block indefinitely

---

## Step 10.1: Add Hystrix/Resilience4j Circuit Breaker

### What
Wrap all inter-service calls with a circuit breaker that prevents cascading failures

### Why
If Ledger Service is down, the gateway shouldn't send 1000 requests that all timeout. Instead, after a few failures, "open the circuit" and reject new requests immediately with a meaningful error.

### Concept: Circuit Breaker States
```
CLOSED (normal):      Let requests through
OPEN (failing):       Reject requests immediately
HALF-OPEN (probing):  Try a test request to see if service recovered
```

### High-Level Approach
Add Resilience4j dependency to gateway-service pom.xml.
Create `GatewayConfig` with circuit breaker configuration:
```
resilience4j:
  circuitbreaker:
    instances:
      auth-service:
        registerHealthIndicator: true
        slidingWindowSize: 10        # look at last 10 calls
        failureRateThreshold: 50     # if 50% fail, open circuit
        waitDurationInOpenState: 30000  # wait 30s, then try HALF-OPEN
      ledger-service:
        # same config
```

Apply @CircuitBreaker annotation to inter-service calls, or configure in RouteLocator.

✅ **Checkpoint:** Stop ledger-service. Try to call /api/accounts → first few requests fail with timeout, then circuit opens and returns 503 immediately. Start ledger-service. Wait 30 seconds. Next request is a probe. If it succeeds, circuit closes and requests go through again.

---

## Step 10.2: Add Retry Logic with Backoff

### What
Automatically retry failed requests with exponential backoff before opening the circuit

### Why
Transient failures (brief network blip, temporary overload) should recover on retry. Don't immediately assume the service is down.

### High-Level Approach
Add retry configuration:
```
resilience4j:
  retry:
    instances:
      ledger-service:
        maxAttempts: 3
        waitDuration: 1000       # 1 second between retries
        intervalFunction: exponentialBackoff  # 1s, 2s, 4s
```

On a transient failure, retry immediately. On persistent failure (3 retries exhausted), then open circuit.

✅ **Checkpoint:** Temporarily kill ledger-service, make a request → fails immediately (no retry). Restart and make another request → succeeds without client needing to retry.

---

# 🔍 FEATURE 11: Search, Filtering & Pagination

## User Story
*"As an API consumer, I want to query transactions with filters (date range, status, amount threshold) and get paginated results so I can build reporting dashboards and audit views"*

## What You're Building
```
GET /api/transactions?status=SETTLED&from=2026-01-01&to=2026-06-30&limit=50&offset=0
    → [{id, amount, status, createdAt, ...}, ...]
    → response headers: X-Total-Count: 1234, X-Page: 1, X-Page-Size: 50

GET /api/accounts?type=ASSET&search=checking&limit=20&offset=0
    → list of accounts matching criteria
```

## Why This Matters
Without search/filtering:
- GET /api/transactions returns ALL transactions (could be millions)
- Slow, memory-intensive, breaks frontend
- Can't audit specific date ranges or statuses
- Real-world apps are unusable without this

---

## Step 11.1: Add Pagination with Limit/Offset

### What
Modify transaction and account endpoints to accept limit and offset parameters, returning only a slice of results

### Why
Prevent memory explosions. Large result sets crash clients and servers.

### High-Level Approach
Add `@RequestParam int limit = 20, @RequestParam int offset = 0` to list endpoints.

Use Spring Data's `Pageable`:
```java
@GetMapping
public Page<TransactionResponse> listTransactions(
    @ParameterObject Pageable pageable
) {
    return transactionService.findAll(pageable);
}
```

Pageable automatically handles limit/offset. Response includes:
- totalElements, totalPages, currentPageNumber, pageSize

✅ **Checkpoint:** POST 100 transactions. GET /api/transactions?limit=10&offset=0 returns 10 items. GET with offset=10 returns next 10. Total count in response header: 100.

---

## Step 11.2: Add Filtering by Status, Date, Amount

### What
Accept query parameters for filtering: status, dateFrom, dateTo, minAmount, maxAmount

### Why
Clients need to find transactions matching specific criteria. Without this, they download all transactions and filter in-app (slow, wastes bandwidth).

### High-Level Approach
Extend TransactionRepository with custom `@Query`:
```java
@Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.createdAt BETWEEN :from AND :to")
Page<Transaction> findByStatusAndDateRange(
    @Param("status") TransactionStatus status,
    @Param("from") LocalDateTime from,
    @Param("to") LocalDateTime to,
    Pageable pageable
);
```

Modify TransactionController:
```java
@GetMapping
public Page<TransactionResponse> list(
    @RequestParam(required = false) TransactionStatus status,
    @RequestParam(required = false) LocalDate from,
    @RequestParam(required = false) LocalDate to,
    @RequestParam(required = false) BigDecimal minAmount,
    @ParameterObject Pageable pageable
) {
    return transactionService.search(status, from, to, minAmount, pageable);
}
```

✅ **Checkpoint:** POST 10 transactions with various statuses and dates. GET /api/transactions?status=SETTLED&from=2026-06-01&to=2026-06-30 returns only matching transactions. GET without filters returns all.

---

## Step 11.3: Add Full-Text Search (Optional Advanced)

### What
Search account names, transaction descriptions using full-text index

### Why
Production reporting needs to find "all accounts containing 'checking'" or "all transactions mentioning 'payment'".

### High-Level Approach (PostgreSQL)
Add full-text search column to accounts and transactions tables:
```sql
ALTER TABLE accounts ADD COLUMN search_vector tsvector;
CREATE INDEX idx_search_vector ON accounts USING gin(search_vector);
```

Update via trigger when name changes. Query via @Query with `@@` operator:
```java
@Query(nativeQuery = true, value = "SELECT * FROM accounts WHERE search_vector @@ plainto_tsquery(:query)")
List<Account> searchByName(@Param("query") String query);
```

✅ **Checkpoint:** Create account "Checking Account". Search GET /api/accounts?search=checking → returns the account. Search ?search=saving → returns empty. Search ?search=check → returns account.

---

# 📋 FEATURE 12: Audit Logging & Compliance

## User Story
*"As a compliance officer, I need to audit who performed what action, when, and from where — to satisfy regulatory requirements and detect fraud"*

## What You're Building
```
Every account creation, transaction post, state change, and reversal is logged:
{
  "timestamp": "2026-06-21T10:15:23Z",
  "action": "TRANSACTION_POSTED",
  "userId": "abc-123",
  "ipAddress": "192.168.1.100",
  "correlationId": "xyz-789",
  "entityType": "TRANSACTION",
  "entityId": "txn-456",
  "details": {
    "fromAccount": "acc-111",
    "toAccount": "acc-222",
    "amount": 500.00,
    "currency": "INR",
    "status": "SETTLED"
  },
  "changes": {  // if updating
    "before": {"status": "PENDING"},
    "after": {"status": "SETTLED"}
  }
}
```

These logs are immutable and searchable for compliance audits.

## Why This Matters
Without audit logging:
- "How much did user X withdraw last month?" → can't answer
- "Who reversed this transaction?" → no record
- Regulatory audit → fail (no evidence of controls)
- Fraud investigation → no trail

With audit logging:
- Every mutation is recorded with who, what, when, where
- Immutable (can't be altered after the fact)
- Searchable by user, entity, date, action
- Demonstrates compliance to regulators

---

## Step 12.1: Create Audit Log Schema

### What
Add an audit_logs table that records every write operation in the system

### Why
Immutable append-only log of all mutations. Can never be deleted or updated.

### High-Level Approach
Create Liquibase migration `005-create-audit-logs.yaml` for ledger-service:
```sql
CREATE TABLE audit_logs (
  id UUID PRIMARY KEY,
  timestamp TIMESTAMPTZ NOT NULL,
  action VARCHAR(50) NOT NULL,  -- ACCOUNT_CREATED, TRANSACTION_POSTED, etc.
  userId UUID,                   -- who did it
  ipAddress VARCHAR(50),         -- from where
  correlationId VARCHAR(100),    -- trace it back
  entityType VARCHAR(50) NOT NULL,  -- ACCOUNT, TRANSACTION, etc.
  entityId UUID NOT NULL,        -- which account/transaction
  details JSONB,                 -- what changed
  created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entityType, entityId);
CREATE INDEX idx_audit_logs_user ON audit_logs(userId, timestamp DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs(action, timestamp DESC);
```

Key: Never update or delete audit logs. Append-only.

✅ **Checkpoint:** Table exists, no triggers or update statements touch it ever. Only INSERTs.

---

## Step 12.2: Log All Write Operations

### What
Create an AuditLogger service that records every CREATE, UPDATE, DELETE operation

### Why
Centralize audit logic so it's consistent across services.

### High-Level Approach
Create `AuditLogger` service:
```java
@Service
public class AuditLogger {
    @Inject private AuditLogRepository auditLogRepository;
    @Inject private HttpServletRequest request;
    
    public void logAction(String action, String entityType, UUID entityId, Object details) {
        String userId = request.getHeader("X-User-Id");
        String ipAddress = request.getRemoteAddr();
        String correlationId = MDC.get("correlationId");
        
        AuditLog log = new AuditLog(
            UUID.randomUUID(),
            Instant.now(),
            action,
            userId,
            ipAddress,
            correlationId,
            entityType,
            entityId,
            toJson(details)
        );
        auditLogRepository.save(log);
    }
}
```

Call this in service methods:
```java
// In AccountService.createAccount()
Account account = accountRepository.save(new Account(...));
auditLogger.logAction("ACCOUNT_CREATED", "ACCOUNT", account.getId(), account);

// In TransactionService.postTransfer()
Transaction txn = transactionRepository.save(transaction);
auditLogger.logAction("TRANSACTION_POSTED", "TRANSACTION", txn.getId(), txn);
```

✅ **Checkpoint:** Create an account via API. Query audit_logs table — row exists with action=ACCOUNT_CREATED, userId set, entityId=account id.

---

## Step 12.3: Create Audit Query Endpoints

### What
Expose endpoints so operators/auditors can search audit logs

### Why
Compliance investigations need to answer "What happened to account X between dates Y and Z?"

### High-Level Approach
Create AuditController with endpoints:
```
GET /api/audit/logs?entityType=ACCOUNT&entityId=...&from=...&to=...&limit=100
GET /api/audit/logs?action=TRANSACTION_POSTED&userId=...&from=...&to=...
GET /api/audit/logs?correlationId=abc-123  -- trace one request
```

Require special role: `ROLE_AUDITOR` or `ROLE_COMPLIANCE`.

Query `audit_logs` with appropriate filters, return paginated AuditLogResponse.

✅ **Checkpoint:** Call GET /api/audit/logs?action=TRANSACTION_POSTED&from=2026-06-01&to=2026-06-30 → returns all transaction posts in that month. Verify each includes userId, ipAddress, correlationId.

---

## Step 12.4: Immutability & Retention Policy

### What
Ensure audit logs can never be modified or deleted (except by database admin), and configure retention

### Why
Regulatory compliance: "Prove you can't alter audit logs to cover up fraud."

### High-Level Approach
1. Grant minimal permissions: service user has INSERT only, no UPDATE/DELETE on audit_logs
2. Set up read-only backup/archive: daily snapshots to immutable storage
3. Configure retention: keep logs for minimum 7 years (fintech requirement)
4. Disable application-level deletes: no code path should ever delete audit logs

```sql
-- Only the service user can INSERT
GRANT INSERT ON audit_logs TO ledger_app_user;
REVOKE UPDATE, DELETE ON audit_logs FROM ledger_app_user;

-- Only superuser can delete (for compliance review, with log)
```

✅ **Checkpoint:** Try to delete an audit log row via application code — fails (no DELETE permission). Try via direct SQL as service user — fails. Only superuser can delete (proves intentionality for audit trail).

---

# Summary: Production-Ready Checklist

After Features 0-12, you have:
- ✅ Microservices with Eureka service discovery
- ✅ JWT auth and token refresh
- ✅ API Gateway with middleware (rate limiting, correlation ID, JWT validation)
- ✅ Double-entry accounting with immutable journal
- ✅ Idempotency protection (duplicate-safe payments)
- ✅ Async events with Kafka
- ✅ Reconciliation engine (daily verification)
- ✅ Redis caching (sub-50ms balance reads)
- ✅ Observability (correlation IDs, structured logging)
- ✅ Resilience (circuit breakers, automatic recovery)
- ✅ Search & filtering (reports, dashboards)
- ✅ Audit logging (compliance, fraud detection)

**This is what separates a portfolio project from a production system.** You can now say in interviews:
> *"I built a production-grade fintech ledger. It handles distributed tracing, async events, resilience patterns, audit logging, idempotency, and reconciliation — the same patterns used by real payment systems."*

---

## 🎤 What to Say in an Interview

Lead with the **problems you solved**, not the features you built:

> *"I built a microservices payment ledger with Spring Security, Kafka, and distributed tracing. The interesting part was handling concurrent duplicate requests — two identical payment requests arriving at the same millisecond both pass a simple unique key check before either commits. I solved it with PostgreSQL's `INSERT ... ON CONFLICT DO NOTHING` so the second request always replays the original response — not an error, not a new charge."*

> *"The journal is completely immutable. Nothing is ever updated or deleted. A reversal doesn't edit the original entries — it creates new counter-entries. You can reconstruct the exact balance at any point in history by replaying entries up to that timestamp. This is how real fintech systems work."*

> *"The reconciliation engine verifies that for any time window, the sum of all debit entries equals the sum of all credit entries. If they ever drift, there's a bug. Real fintech companies run this check every single day. I added it to the system so I understand compliance and auditing."*

> *"Every request gets a correlation ID that flows through logs, database operations, and Kafka events. Search one ID and trace the entire transaction lifecycle across all microservices. This is production observability."*

---

## 📌 How to Use This File

When you're ready to start a phase:

> *"I'm building a microservices payment ledger. Here's my roadmap [point to this file]. I want to start [Feature X]. Help me [design the schema / build the service / test the logic]."*

Each feature is scoped small enough to complete in one session. Mark checkboxes as you complete steps. Never skip features — each one's exit criteria feed into the next.

---

## 🎯 Production-Grade Audit: Missing Features

After completing Features 0-12, you have the **core** of a production system. Here's what separates it from enterprise-grade:

| What You Have | What You're Missing | Why It Matters |
|---|---|---|
| Account balances | Transaction limits + velocity checks | Fraud prevention: "User withdrew $50k in 5 minutes" → block |
| Transfers | Fee deductions | Revenue model: charge users/merchants per transaction |
| JWT auth | Role-based access control (RBAC) | Admins can freeze accounts, view user data without giving them power |
| Kafka events | Webhook delivery to clients | Partners/apps need real-time push: "Your payment cleared" |
| Input validation in services | Gateway-level request validation | Catch injection attacks before they hit the database |
| Append-only audit logs | Immutable backups & disaster recovery | Regulatory: "Prove you can restore to any point in time" |
| Single version API (/api/) | Multiple versions (v1/, v2/) | Rolling upgrades: old clients still work while new ones use v2 |
| Manual load testing | Automated performance benchmarks | Know your system breaks at 10k req/sec, not when users discover it |

---

## 🏆 What Makes This Production-Grade

**Minimum (Features 0-12):**
- Handles double-entry with immutable audit trail ✅
- Prevents duplicate charges (idempotency) ✅
- Distributes load across services ✅
- Recovers from service failures (circuit breakers) ✅
- Traces requests across all systems ✅
- Denies unauthorized access at the gateway ✅
- Can audit "who did what when" ✅

**Enterprise (Add Features 13-20):**
- Prevents fraud via limits + risk scoring
- Monetizes via fees and subscriptions
- Can rotate code without breaking clients (versioning)
- Fine-grained permissions (RBAC)
- Pushes real-time events to external systems (webhooks)
- Defends against injection attacks (validation)
- Recovers from data corruption (backup/restore)
- Knows performance limits and SLAs (benchmarking)

---

## 🎯 Recommended Path Forward

**After Features 0-12, prioritize in this order:**
1. **Feature 13** (Limits) — Fraud prevention is non-negotiable
2. **Feature 14** (Fees) — Revenue model, shows real business logic
3. **Feature 15** (Versioning) — API longevity matters
4. **Feature 16** (RBAC) — Demonstrates access control maturity
5. **Feature 17** (Webhooks) — Shows async integration patterns (like Kafka, but outbound)
6. **Feature 18** (Validation) — Security awareness (input sanitization, rate limiting per endpoint)
7. **Feature 19** (Backup/Recovery) — Disaster recovery story for compliance
8. **Feature 20** (Load Testing) — Performance profile and capacity planning

You don't need all 20 to impress interviewers. **Features 0-12 are rock-solid.** Features 13-16 are the "wow" additions that show you understand full-stack fintech.
