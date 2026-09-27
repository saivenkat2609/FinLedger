# FinLedger — Project Review & Interview Prep

---

## Part 1: What's Wrong / What's Missing (Honest Assessment)

### Critical Bugs (Would Break in Production)

#### 1. `settledAt` is marked `updatable = false` — reconciliation always returns zero
`Transaction.java` line 47 has `@Column(updatable = false)` on `settledAt`. When `postTransfer()` calls `setSettledAt()` and then saves the entity, Hibernate silently excludes that column from the UPDATE SQL. Every transaction has `settledAt = null` in the database, so all reconciliation queries that filter by date range return zero rows and always report a perfectly balanced ledger — completely useless.

#### 2. Cache key mismatch — discrepancy check always flags every account as MISSING_CACHE
`AccountService.getAccountBalance()` uses `@Cacheable(value = "balances")`, which stores keys in Redis as `balances::uuid`. `ReconciliationService.findDiscrepancies()` manually reads Redis using `"balance:" + id` (singular, colon separator). These never match, so every account appears to have a missing cache entry. The discrepancy report is entirely broken.

#### 3. JWT validation — gateway calls POST, auth-service listens on GET
`JwtValidationFilter` sends `webClient.post().uri("/auth/validate")`. `AuthController` has `@GetMapping("/validate")`. Every JWT check returns `405 Method Not Allowed`, causing every protected API request to fail with 401. The entire authenticated API is inaccessible unless you bypass the gateway.

#### 4. `DeadLetterPublishingRecoverer` constructed with null — notification-service crashes on startup
`KafkaConsumerConfig.deadLetterPublishingRecoverer()` passes a lambda and a private method that returns `null` to the constructor. The result is either a class-cast exception or a NullPointerException during Spring context initialization. The notification-service will not start.

#### 5. Dual retry conflict — `@RetryableTopic` and `DefaultErrorHandler` both active on the same listener
`SettlementConsumer` uses `@RetryableTopic` (which creates its own dedicated retry topics and bypasses `DefaultErrorHandler`). `KafkaConsumerConfig` also registers a `DefaultErrorHandler` with `FixedBackOff`. These two conflict, leading to undefined error-handling behavior.

---

### Race Conditions / Data Integrity Issues

#### 6. No database lock on balance read — double-spend is possible
`postTransfer()` reads the balance with `getAccountBalance(sourceAccountId)`, checks if it's sufficient, and then debits. There is no `SELECT FOR UPDATE` (pessimistic lock) or optimistic lock check on the derived balance. Two concurrent requests for the same account can both pass the balance check and both commit, resulting in a negative balance.

#### 7. Idempotency check is not atomic — concurrent duplicates return 500 instead of 409
The `findByIdempotencyKey` lookup and the `save()` are two separate steps. A concurrent duplicate insert will throw `DataIntegrityViolationException` (a DB constraint violation), which the global exception handler catches as a generic 500 instead of a 409 Conflict. Clients get an error response even though the original transaction committed successfully.

---

### Design Gaps (Not Production-Grade)

#### 8. `CacheConfig` is empty — no per-cache TTL, no explicit serialization
The class only has `@EnableCaching`. There's no `RedisCacheManager` bean that registers the `"balances"` cache with explicit TTL, key serializer, or value serializer. It works by accident because `BigDecimal` happens to be JDK-serializable, but there's no control over cache naming, expiry per cache, or eviction policies.

#### 9. Gateway routes a non-existent `reporting-service`
`application.yml` has a route to `lb://reporting-service` for `/reports/**`. No such service exists. Every hit on that path causes a connection-refused error, opens the circuit breaker immediately, and pollutes health metrics.

#### 10. Retry filter configured for POST/PUT/DELETE — unsafe for mutations
The gateway retries `POST`, `PUT`, and `DELETE` on server errors. For `postTransfer`, this replays the same idempotency key, hits the duplicate check, and returns 409 to the client — even though the original request succeeded. Retries should be restricted to `GET`.

#### 11. Refresh tokens accumulate without bound
`AuthService.issueTokens()` always creates a new `RefreshToken` row without revoking or deleting previous ones for the same user. A user who logs in repeatedly will have hundreds of valid refresh tokens simultaneously. There is no cleanup job or expiry enforcement at login time.

#### 12. JWT secret is hardcoded with inconsistent property paths
`auth-service` uses `${JWT_SECRET:hardcoded}` and `ledger-service` uses `${app.jwt.secret:differenthardcoded}`. In Docker the env vars align, but locally the fallback values differ. Secrets should never be in source code or `docker-compose.yml` in plaintext — use Docker secrets, Vault, or a secrets manager.

#### 13. `correlationId` on Transaction is a new random UUID, not the request's correlation ID
`TransactionService` calls `UUID.randomUUID()` instead of reading `MDC.get("X-Correlation-ID")`. The stored correlation ID on the DB record will never match any log line, making distributed tracing from DB queries impossible.

#### 14. Date range ignored in `findDiscrepancies()`
The method accepts `fromDate`/`toDate` parameters but the underlying repository call aggregates all journal entries regardless of date. The parameters are only used in a log message. The method silently computes an all-time balance snapshot regardless of what range you pass.

#### 15. Orphaned `idempotency_keys` table — migration exists, no code uses it
Migration `004-create-idempotency-keys.yaml` creates a table with `key`, `response_payload`, `http_status_code`, `expires_at`. There is no JPA entity, repository, or service that touches it. The actual idempotency logic uses a unique constraint on `transactions.idempotency_key`. The table is dead weight and misleads anyone reading the schema.

#### 16. Two rate-limiting mechanisms active simultaneously
The custom `RateLimitingFilter` (uses `RedisTemplate`) and Spring Cloud Gateway's built-in `RequestRateLimiter` route filter are both configured and active. A request is rate-limited twice, by two different counters, with different limits. This is unintentional and creates confusing 429 behavior.

#### 17. CORS wildcard headers + credentials violates the spec — browser requests fail
`CorsConfig` sets `allowedHeaders("*")` and `allowCredentials(true)`. The W3C CORS spec explicitly forbids combining these. Browsers will reject preflight responses, silently breaking every authenticated request from a frontend app.

#### 18. `System.out.println` in `JwtAuthenticationFilter`
JWT validation errors are logged with `System.out.println` instead of the SLF4J logger. These lines bypass Logback, bypass MDC, don't appear in the JSON log file, and can't be filtered or aggregated in any log system.

#### 19. Near-zero test coverage
Only 2 test files exist across 5 services. There are no unit tests for `TransactionService`, `AccountService`, `ReconciliationService`, or `AuthService`. No controller tests (`@WebMvcTest`/MockMvc), no contract tests between gateway and services, no integration tests for Kafka flows. The one load test (`CacheLoadTest`) does a self-transfer (source == destination), which produces zero net balance change, so it tests nothing meaningful.

#### 20. `CacheLoggingAspect` uses a 5ms threshold to guess cache hits
`duration < 5` is used to determine if a cache hit occurred. Redis round-trips in Docker can range from 1–20ms under load; DB queries can return in under 1ms from cache. This heuristic is wrong in both directions and produces misleading debug logs.

---

### What a Real-World Version Would Have

| Area | What's Missing |
|---|---|
| **Observability** | Distributed tracing (Micrometer + Zipkin/Jaeger), metrics endpoints (Prometheus/Grafana), health dashboards |
| **Security** | Secrets management (Vault/AWS Secrets Manager), token rotation, mTLS between services |
| **Testing** | Unit tests for all services, contract tests (Pact), integration tests with Testcontainers, load tests |
| **Deployment** | Kubernetes manifests, Helm charts, resource limits, liveness/readiness probes, HPA |
| **Resilience** | Saga pattern or 2PC for distributed transactions, outbox pattern for Kafka reliability |
| **Data** | DB migration rollback scripts, connection pool tuning (HikariCP settings), read replicas |
| **API** | OpenAPI/Swagger docs, API versioning strategy, pagination on list endpoints |
| **Ops** | Graceful shutdown configuration, rolling deploys, canary releases, runbooks |
| **Audit** | Immutable audit log table, event sourcing for financial compliance |
| **Notification** | Actual notification channels (email/SMS), retry dashboards, DLT monitoring |

---

## Part 2: Interview Questions & Short Answers

---

### Architecture & Design

**Q1. Walk me through the overall architecture of this system.**

This is a microservices-based double-entry ledger system. It has five services: an Eureka service registry, an auth-service for JWT authentication, a ledger-service for core financial operations (accounts, transactions, journal entries, reconciliation), a notification-service that consumes Kafka events, and a Spring Cloud Gateway that handles routing, circuit breaking, and rate limiting. Infrastructure includes PostgreSQL, Redis, and Kafka.

---

**Q2. Why did you choose Spring Cloud Gateway over Nginx or a simple load balancer?**

Spring Cloud Gateway is code-driven and integrates natively with Spring Boot. It lets you wire circuit breakers (Resilience4j), rate limiting (Redis token bucket), and JWT validation as composable filter chains alongside service discovery (Eureka). Nginx requires separate configuration files and doesn't have native circuit breaker support. The tradeoff is that Gateway is JVM-based and heavier than Nginx; for a high-traffic setup I'd put Nginx in front of the Gateway or switch to a dedicated API gateway product.

---

**Q3. Why use a service registry (Eureka) instead of hardcoded service URLs?**

Service instances in a containerized environment get dynamic IPs and ports. Eureka lets services register themselves and lets clients discover them by logical name (e.g. `lb://ledger-service`). This enables horizontal scaling without config changes. The alternative is DNS-based service discovery (which Kubernetes provides natively), making Eureka less necessary in a k8s environment.

---

**Q4. What is double-entry bookkeeping and why does your ledger use it?**

Every financial transaction has two sides — a debit on one account and an equal credit on another. The sum of all debits must equal the sum of all credits. This invariant makes it impossible for money to appear or disappear; any discrepancy indicates a bug or fraud. The ledger enforces this by always creating exactly two `JournalEntry` rows (one DEBIT, one CREDIT) in the same transaction, then asserting they balance before committing.

---

**Q5. What is the Outbox Pattern and why didn't you implement it?**

The Outbox Pattern solves the dual-write problem: you need to write to the DB and publish to Kafka atomically. Without it, the DB write can succeed but the Kafka publish can fail (or vice versa), leaving the system inconsistent. The correct approach is to write the event to an `outbox` table in the same DB transaction, then have a separate poller (Debezium CDC or a scheduled job) publish it to Kafka and delete it from the outbox. In this project, `TransactionEventPublisher` publishes directly after `transactionRepository.save()` — if Kafka is down, the event is silently lost.

---

**Q6. What is the Saga Pattern and when would you use it here?**

A Saga coordinates a distributed transaction across multiple services using a sequence of local transactions, each publishing an event that triggers the next step. If any step fails, compensating transactions roll back previous steps. For a payment flow spanning ledger-service (debit source), ledger-service (credit destination), and notification-service (notify), a Saga ensures consistency without a distributed lock. This project doesn't implement Sagas — `postTransfer` does both debits and credits locally in one DB transaction, which works only because both operations are in the same service.

---

**Q7. Why is idempotency important in a financial API?**

Network failures cause clients to retry requests. Without idempotency, a retry on a `POST /transactions` creates a duplicate transaction — the user is charged twice. The solution is an idempotency key (a unique UUID the client generates per request). The server checks if it's seen that key before; if yes, it returns the cached response without re-executing. This project accepts an `X-Idempotency-Key` header and stores it as a unique constraint on the `transactions` table.

---

### Resilience & Reliability

**Q8. What is a Circuit Breaker and how does it work in this project?**

A circuit breaker monitors calls to a downstream service. In the CLOSED state, calls go through normally. When the failure rate exceeds a threshold (here 50% over a 10-call sliding window), it trips to OPEN and immediately rejects all calls with a fallback response (503). After a wait period (30s), it transitions to HALF_OPEN and allows a probe request. If that succeeds, it resets to CLOSED; if it fails, it stays OPEN. In this project Resilience4j circuit breakers are wired in the gateway for `auth-service` and `ledger-service`.

---

**Q9. What's the difference between Circuit Breaker, Retry, and Timeout — and should you always combine them?**

- **Timeout**: cap how long you wait for a response.
- **Retry**: re-attempt a failed request N times with backoff.
- **Circuit Breaker**: stop attempting when a service is clearly degraded.

You should combine them carefully. Retry without a circuit breaker hammers a failing service. Circuit breaker without timeout can leave threads hanging. Retry on non-idempotent operations (POST) can cause duplicate side effects. In this project, retrying POST requests through the gateway is a mistake.

---

**Q10. What is a DLT (Dead Letter Topic) in Kafka and why is it important?**

When a Kafka consumer fails to process a message after all retries, the message goes to a Dead Letter Topic instead of being lost. This allows engineers to inspect failed messages, fix the bug, and replay them. `SettlementConsumer` uses `@RetryableTopic` (3 retry topics, 4 total attempts) with a `@DltHandler` that logs the failure. In a real system the DLT handler would page an on-call engineer and store the payload for replay.

---

**Q11. How does `@RetryableTopic` work in Spring Kafka?**

It creates N separate retry topics (e.g. `transaction-settled-retry-0`, `-retry-1`, `-retry-2`). When a message fails, instead of immediately re-trying on the same partition (which would block newer messages), it publishes the message to the next retry topic with a delay header. Each retry topic has its own consumer that enforces the delay before processing. This is non-blocking retry — the main topic continues to process newer messages while failed ones retry in the background.

---

**Q12. What is Redis used for in this project?**

Two things: caching and rate limiting. Account balances are cached in Redis with a 5-minute TTL using Spring Cache (`@Cacheable`/`@CacheEvict`). The rate limiter in `RateLimitingFilter` uses Redis `INCR` and `EXPIRE` to count requests per IP per window. Redis is appropriate here because it's fast (sub-millisecond reads), supports TTL natively, and is shared across multiple gateway instances.

---

### Spring Boot & Java

**Q13. What is `@Transactional` and what are its pitfalls?**

`@Transactional` wraps a method in a DB transaction — either committing everything or rolling back on exception. Key pitfalls: (1) it only works on Spring-managed beans (self-invocation bypasses the proxy), (2) by default only rolls back on unchecked exceptions — checked exceptions commit, (3) `REQUIRED` propagation (default) can silently join an outer transaction, masking failures, (4) long transactions holding locks cause contention. In `postTransfer`, the entire balance check + journal entry creation + save is one transaction, which is correct for atomicity.

---

**Q14. What is the difference between `@Cacheable` and `@CacheEvict`?**

`@Cacheable` checks the cache before executing the method; if a value exists for the key it returns it without calling the method. `@CacheEvict` removes an entry (or all entries) from the cache after the method executes. In this project, `getAccountBalance()` is annotated with `@Cacheable` and `postTransfer()` uses `@CacheEvict` on both source and destination account IDs to invalidate stale balances after a transaction commits.

---

**Q15. What is an AOP Aspect and how is it used here?**

Aspect-Oriented Programming lets you inject behavior at method call boundaries without modifying the method itself. `CacheLoggingAspect` uses `@Around("execution(* ...AccountService.getAccountBalance(..))")` to measure how long the balance lookup takes and log whether it was likely a cache hit or a DB call. This keeps the timing/logging logic separate from the business logic.

---

**Q16. What is a Spring Cloud Gateway filter chain and how does request processing work?**

Gateway processes each request through an ordered chain of filters (both global and route-specific). Global filters apply to every request (e.g. `CorrelationIdFilter`, `RateLimitingFilter`). Route filters apply only to matching routes (e.g. `CircuitBreaker`, `Retry`). Filters have a `pre` phase (before forwarding) and a `post` phase (on the response). The `JwtValidationFilter` runs in pre-phase — it validates the token and either proceeds or short-circuits with a 401.

---

**Q17. What is `@Version` in JPA and what does it prevent?**

`@Version` adds optimistic locking. JPA adds a `WHERE version = N` clause to UPDATE statements. If two transactions read the same row and both try to update it, the second one finds `version != N` and throws `OptimisticLockException`. This prevents lost updates — the second writer knows it read stale data and must retry. `Transaction` and `JournalEntry` have `@Version` fields, but the balance check (which is a derived aggregate, not a row update) isn't protected by it.

---

**Q18. What is Liquibase and why use it over writing SQL directly?**

Liquibase is a database migration tool. Migrations are versioned YAML/XML/SQL changesets that are applied once and tracked in a `DATABASECHANGELOG` table. Using it means: schema changes are in source control, rollbacks are explicit (not accidental drops), and environments (local/staging/prod) always run the same migration history. Writing SQL directly in scripts risks drift between environments and makes rollbacks error-prone.

---

### Security

**Q19. What is a JWT and how does auth flow work in this project?**

A JWT (JSON Web Token) is a signed token containing claims (user ID, roles, expiry). Auth flow: (1) Client POSTs credentials to `auth-service /auth/login`. (2) Auth-service validates credentials, issues a short-lived access token (15min) and a long-lived refresh token (7 days). (3) Client includes `Authorization: Bearer <token>` on subsequent requests. (4) Gateway's `JwtValidationFilter` calls `auth-service /auth/validate`. (5) If valid, the gateway forwards `X-User-Id` and `X-User-Role` headers to downstream services. (6) Downstream services trust these headers because they come through the gateway.

---

**Q20. What's the difference between authentication and authorization?**

Authentication answers "who are you?" — verifying identity (username + password → JWT). Authorization answers "what are you allowed to do?" — enforcing permissions (admin can access all accounts, user can only access their own). In this project, auth-service handles authentication. Authorization is minimal — `ledger-service` reads `X-User-Role` from the forwarded header but doesn't enforce fine-grained resource ownership (a user could theoretically access any account by ID).

---

**Q21. What is token refresh and why do you need a refresh token at all?**

Access tokens are short-lived (15min) to limit the damage of a stolen token — it expires quickly. But requiring users to re-login every 15 minutes is bad UX. Refresh tokens are long-lived (7 days) and are used only to get a new access token without re-entering credentials. If a refresh token is stolen, it can be revoked server-side (by deleting it from the DB), which access tokens (being stateless JWTs) cannot be. This is the access token / refresh token split.

---

**Q22. What CORS issue exists in this project?**

`CorsConfig` sets `allowedHeaders("*")` (wildcard) and `allowCredentials(true)` simultaneously. The CORS spec forbids this combination — a wildcard allowed-headers header combined with credentials causes browsers to reject the preflight response. All authenticated browser requests fail silently. The fix is to explicitly enumerate the allowed headers (`Authorization`, `Content-Type`, `X-Idempotency-Key`) instead of using a wildcard.

---

### Kafka & Event-Driven

**Q23. What is Kafka and why use it here instead of synchronous HTTP calls?**

Kafka is a distributed log. Producers append messages to topics; consumers read them at their own pace. It decouples services in time — the ledger-service publishes `TransactionSettledEvent` and doesn't wait for the notification-service to process it. Benefits: services can fail independently without affecting each other, events are durable (replayed if a consumer was down), and multiple consumers can subscribe to the same events independently. The tradeoff is eventual consistency — the notification is delayed, not immediate.

---

**Q24. What is at-least-once vs exactly-once delivery in Kafka?**

- **At-least-once**: the consumer commits its offset only after processing. If it crashes after processing but before committing, it reprocesses the message on restart. Messages may be processed more than once.
- **Exactly-once**: Kafka transactions + idempotent producers ensure each message is processed exactly once. More complex and slower.
- **At-most-once**: offset committed before processing. If crash occurs, message is lost.

This project uses at-least-once (the default). The notification handler should be idempotent (sending a duplicate notification is acceptable) to tolerate redelivery.

---

**Q25. What is consumer group rebalancing in Kafka?**

Consumer groups allow multiple instances of a service to share the work of consuming a topic. Each partition is assigned to exactly one consumer in the group. When a consumer joins or leaves (scaling up/down, restart), Kafka triggers a rebalance — redistributing partitions among the current members. During a rebalance, consumption pauses briefly. In production you should tune `session.timeout.ms` and `heartbeat.interval.ms` to balance between detecting failed consumers quickly and causing unnecessary rebalances.

---

### Observability

**Q26. What is a correlation ID and how does it flow through this system?**

A correlation ID is a unique identifier (UUID) assigned to each incoming request. Every log line in every service includes this ID in the MDC (Mapped Diagnostic Context). When you search your log aggregator for a specific correlation ID, you see the complete request trace across all services. In this project, `CorrelationIdFilter` in each service reads `X-Correlation-ID` from the incoming header (or generates a new UUID if absent) and puts it into MDC. The gateway forwards the header downstream so all services log the same ID for the same user request.

---

**Q27. What is structured logging and why does it matter?**

Structured logging means emitting logs as JSON objects (with fields like `timestamp`, `level`, `correlationId`, `message`, `service`) instead of plain text strings. Log aggregators (ELK stack, Datadog, Splunk) can index, filter, and query JSON fields directly — e.g. `correlationId = "abc-123"` or `level = "ERROR" AND service = "ledger-service"`. Plain text requires regex parsing, which is fragile and slow. This project uses `logback-spring.xml` with the Logstash JSON encoder to emit structured JSON logs.

---

**Q28. What would you add for proper distributed tracing?**

Distributed tracing (Micrometer Tracing + Zipkin or Jaeger) automatically instruments every HTTP call and Kafka publish/consume with a `traceId` and `spanId`. The trace ID is propagated through headers (W3C `traceparent` or Zipkin B3). You get a visual flame chart showing exactly how long each service spent, where latency is, and which calls failed — without manually searching logs. This project has correlation IDs (manual tracing) but not automatic distributed tracing.

---

### Data & Persistence

**Q29. What is connection pooling and why does it matter?**

Without pooling, every DB operation opens and closes a TCP connection — expensive (10–100ms overhead). A connection pool (HikariCP is Spring Boot's default) keeps a set of open connections and hands them out to threads on demand. Key settings: `maximum-pool-size` (max concurrent connections, default 10), `connection-timeout` (how long to wait for a connection from the pool), `idle-timeout`. This project uses default HikariCP settings, which is fine for development but needs tuning for production based on DB server capacity and expected concurrency.

---

**Q30. Why is the balance not stored as a column on the Account table?**

In double-entry bookkeeping, the balance is derived — it's the sum of all DEBIT journal entries minus all CREDIT journal entries for that account. Storing it as a column creates a dual-write problem: you'd have to update both the journal entries and the account balance column atomically, and they could drift if a bug skips the update. Computing balance from journal entries is always consistent. The tradeoff is query performance — the balance query aggregates potentially thousands of rows; a cached balance (Redis) solves the read performance problem while keeping the source of truth in journal entries.

---

**Q31. What is an N+1 query problem?**

When loading a list of N entities and then issuing one query per entity to load a related entity, resulting in N+1 total queries. For example, loading 100 accounts and then calling `getAccountBalance(id)` for each one would make 101 DB round trips. Solutions: JOIN FETCH in JPQL, batch fetching, or loading all balances in a single aggregation query. This project's reconciliation `findDiscrepancies()` iterates all accounts and calls the balance repository per account — an N+1 pattern that will be very slow with many accounts.

---

### Microservices & Scalability

**Q32. What are the drawbacks of running this as microservices vs a monolith?**

For this scale (5 services, 1 developer), microservices add significant overhead: distributed debugging, inter-service latency, network failures to handle, separate deployments, and operational complexity (5 JVM processes, separate configs). The current project would run more reliably as a modular monolith. Microservices pay off when teams are large enough to own independent services, when services need to scale independently, or when polyglot technology choices are needed.

---

**Q33. How would you scale the ledger-service horizontally?**

Run multiple instances behind the gateway (which uses `lb://` client-side load balancing via Eureka). The main concern is statelessness — ledger-service has no in-memory state (auth is validated per request, Redis is external). DB is the bottleneck: add a read replica for balance queries, use connection pool tuning, and add an index on `journal_entries(account_id)` for balance aggregation. The race condition on balance checks (double-spend) must also be fixed before horizontal scaling — it's worse with multiple instances.

---

**Q34. What is service discovery and what are the two models?**

Service discovery lets clients find service instances without hardcoding IPs.
- **Client-side** (this project): the client queries Eureka directly and picks an instance to call. The client holds the discovery logic; Ribbon/Spring LoadBalancer does the selection. Simpler to set up, but every client needs a discovery library.
- **Server-side**: the client calls a load balancer (e.g. AWS ALB, Kubernetes Service), which queries the registry and routes the request. The client is discovery-unaware. More infrastructure but simpler clients.

---

**Q35. What would Kubernetes change about this architecture?**

Kubernetes provides its own DNS-based service discovery (Services), making Eureka redundant. It handles container orchestration, restarts on crash, rolling deploys, and horizontal pod autoscaling (HPA). The `docker-compose.yml` would be replaced with Deployments, Services, ConfigMaps (for non-secret config), and Secrets (for JWT keys and DB passwords). Liveness/readiness probes would replace manual health checks. Istio (service mesh) could replace the gateway's circuit breaker and mTLS.

---

**Q36. What is rate limiting and how is it implemented here?**

Rate limiting caps how many requests a client can make in a time window, protecting the service from abuse and DDoS. In `RateLimitingFilter`, Redis `INCR` counts requests per client IP per 60-second window; if the count exceeds 100, the request is rejected with 429. The `EXPIRE` command sets the window TTL atomically on the first request. The issue in this project is that the gateway also has Spring Cloud Gateway's built-in `RequestRateLimiter` filter active, so every request is counted twice against two separate limits.

---

**Q37. What is the difference between synchronous and asynchronous communication between services?**

- **Synchronous** (HTTP/gRPC): caller waits for a response. Simple to reason about, strong consistency, but caller is blocked if downstream is slow or unavailable — cascading failures.
- **Asynchronous** (Kafka, RabbitMQ): caller publishes a message and moves on. Services are decoupled — downstream can be down without blocking the caller. Tradeoff: eventual consistency, harder to debug, requires idempotent consumers.

This project uses both: synchronous HTTP between gateway and auth-service/ledger-service, and asynchronous Kafka from ledger-service to notification-service.

---

### Behavioral / Design Thinking

**Q38. If `postTransfer` takes 500ms under load, what would you investigate?**

In order: (1) DB query time — run EXPLAIN on the balance aggregation query, check for missing indexes on `journal_entries(account_id)`, (2) lock contention — multiple transfers to the same account serializing on the DB row lock, (3) Redis latency — check Redis `INFO stats`, connection pool exhaustion, (4) Kafka publish — publishing synchronously inside the transaction adds Kafka round-trip latency; move it to an async outbox pattern, (5) JVM GC pauses — check GC logs for stop-the-world events under load.

---

**Q39. How would you handle a situation where the Kafka broker is down?**

With the current implementation, `TransactionEventPublisher` catches the exception and logs a WARN — the event is silently lost and the notification-service never processes it. The correct approach is the Outbox Pattern: write the event to an `outbox` table in the same DB transaction as the journal entries. A separate polling service (or Debezium CDC) reads uncommitted outbox rows and publishes them to Kafka, deleting them on success. This guarantees the event is eventually published even if Kafka was down at transaction time.

---

**Q40. What would you change if you were productionizing this project tomorrow?**

Priority order: (1) Fix the critical bugs (settledAt persistence, cache key mismatch, JWT method mismatch, Kafka DLT crash). (2) Add a DB-level lock (`SELECT FOR UPDATE`) on balance reads to prevent double-spend. (3) Move JWT secrets to environment variables or a secrets manager — never in source code. (4) Implement the Outbox Pattern for Kafka reliability. (5) Add integration tests with Testcontainers for the core transaction flow. (6) Add Kubernetes manifests with proper resource limits, probes, and secrets. (7) Wire up Micrometer + Prometheus + Grafana for observability.

---
