# FinLedger — Production Gaps & Interview Prep

> **What this file is:** An honest audit of what's built, what's missing, and how to talk about each gap in an interview.  
> Current branch: `added-circuit-breakers-and-performance-optimizations`

---

## What's Already Production-Quality (your strengths to mention)

- Double-entry bookkeeping with ACID transactions
- Idempotency keys on every transfer (duplicate-safe API)
- Optimistic locking (`@Version`) on all entities
- JWT + refresh token auth with BCrypt
- Dual-layer rate limiting (token bucket + hourly counter per user/IP)
- Circuit breaker + retry + time limiter on the gateway (Resilience4j)
- Redis caching with cache eviction on write
- Kafka with dead-letter topics and retry policy
- Structured logging with correlation ID propagation
- Liquibase for schema migrations
- Actuator health endpoints with readiness/liveness groups
- Docker Compose full-stack orchestration

---

## MAJOR GAPS — Grouped by Production Pillar

---

### 1. Observability & Monitoring *(Critical)*

| Missing | Why It Matters | What to Add |
|---|---|---|
| **Distributed tracing** | Correlation IDs exist but traces don't link spans across 5 services. You cannot reconstruct a request's full journey in production. | Spring Cloud Sleuth / Micrometer Tracing + Zipkin or Jaeger. Every service gets a `TraceId` + `SpanId` propagated in headers. |
| **Metrics pipeline** | Actuator exposes `/actuator/prometheus` but nothing scrapes it. You have no dashboards. | Prometheus scrape config → Grafana dashboards. Key metrics: request latency P50/P95/P99, error rate, circuit breaker state, cache hit ratio, Kafka consumer lag. |
| **Alerting** | No alerts defined. A circuit breaker opens and nobody knows for minutes/hours. | Prometheus AlertManager rules: circuit open, Kafka consumer lag > threshold, DB connection pool exhaustion, 5xx spike. |
| **Log aggregation** | `logstash-logback-encoder` is a declared dependency but logs only go to a local file. In a multi-instance deploy, logs are spread across N machines. | ELK stack (Elasticsearch + Logstash + Kibana) or Loki + Grafana. Ship logs centrally. |
| **APM / Error tracking** | No application performance monitoring. You cannot tell which endpoint is slow. | Datadog APM, New Relic, or open-source Micrometer → Prometheus. Track method-level timing. |

**Interview answer:** *"I have Prometheus endpoints and structured JSON logging ready, but the collection layer (Prometheus scraper, Grafana dashboards, log shipper) needs to be wired in. The instrumentation hooks exist — the backends are missing."*

---

### 2. Security *(Critical for FinTech)*

| Missing | Why It Matters | What to Add |
|---|---|---|
| **Secret management** | JWT secret has a hardcoded base64 default in `application.yml`. Anyone with repo access has it. | HashiCorp Vault or AWS Secrets Manager. Secrets injected at runtime, rotated without redeployment. |
| **mTLS between services** | Internal service-to-service calls (gateway → ledger, gateway → auth) are plain HTTP. A compromised container can impersonate any service. | Istio service mesh with mutual TLS, or Spring Cloud Gateway with client certificates. |
| **Audit log / tamper-evident trail** | No append-only record of who did what. Regulators (PCI-DSS, SOX) require this for financial systems. | Separate audit table (never updated, only inserted): `who`, `action`, `entity_id`, `old_value`, `new_value`, `timestamp`. AOP aspect to populate it automatically. Consider an immutable event store. |
| **Field-level encryption** | Sensitive fields (account names, amounts) are stored in plaintext in PostgreSQL. | Encrypt PII fields at the application layer before persisting. Jasypt or application-level AES-256. |
| **Token rotation / revocation** | Access tokens are 24h JWTs with no server-side revocation. A stolen token is valid for 24 hours. | Token blocklist in Redis (just the JTI claim). On logout or suspicious activity, add JTI to blocklist. All validators check blocklist first. |
| **CORS hardening** | `CorsConfig` exists but allowed origins were not locked down (likely `*`). | Restrict to known frontend origins. Never `*` in production. |
| **SQL injection / input sanitization** | Using Spring Data JPA with JPQL (safe), but some native queries exist. Native queries need careful parameter binding. | Audit every `@Query(nativeQuery = true)` to confirm only `?1`/`:param` binding — never string concatenation. |
| **Dependency vulnerability scanning** | No OWASP Dependency-Check or Snyk scan in the build. | Add `dependency-check-maven` plugin to parent POM. Fail build on CVSS score > 7. |

**Interview answer:** *"For a real financial system I'd add Vault for secrets, mTLS between internal services, a tamper-evident audit log, and JWT revocation via Redis blocklist. PCI-DSS and SOX both require audit trails and encrypted storage."*

---

### 3. CI/CD Pipeline *(Critical)*

**Nothing exists** — no `.github/workflows/`, no Jenkinsfile, no GitLab CI.

What a production pipeline needs:

```
Commit → Build & Test → SAST Scan → Docker Build → Push to Registry
       → Deploy to Staging → Integration Tests → Performance Tests
       → Manual Approval → Deploy to Production → Smoke Tests
```

| Stage | Tool Options |
|---|---|
| Build & unit tests | Maven + GitHub Actions / Jenkins |
| Static analysis (SAST) | SonarQube, SpotBugs, PMD |
| Dependency vulnerabilities | OWASP Dependency-Check, Snyk |
| Docker image build | Buildkit with layer caching |
| Image scanning | Trivy, Grype |
| Container registry | ECR, GCR, Docker Hub |
| Deploy to staging | Kubernetes rolling update / Helm |
| Integration tests | Testcontainers-based test suite |
| Performance tests | Gatling or k6 |
| Production deploy | Blue-green or canary via Argo Rollouts |
| Smoke tests | Curl/Postman scripts against health endpoints |

**Interview answer:** *"The code is containerized and the services register with Eureka, so they're deploy-ready. What's missing is the automation layer: a pipeline that builds, scans, tests, and promotes images through environments without human intervention."*

---

### 4. Multi-Instance Architecture & Distributed Systems *(Critical)*

This is the biggest conceptual gap. The app runs as a single instance of each service.

#### 4a. Stateful Session / Caching Issues with Multiple Instances

| Problem | Current State | Fix |
|---|---|---|
| **Redis cache consistency** | Works fine for 1 instance. With N instances, cache eviction fires only on the instance that processed the write. Other instances serve stale cached balances. | Already using Redis (centralized) — this is actually fine. The `@CacheEvict` hits Redis directly. ✓ But needs verification under network partition. |
| **Rate limiter consistency** | Custom `RateLimitingFilter` uses `RedisTemplate` — centralized, so it works across instances. ✓ | Already correct, just needs to be documented. |
| **Idempotency key storage** | Stored in PostgreSQL — shared across all instances. ✓ | Correct. |
| **In-memory circuit breaker state** | Resilience4j circuit breaker state is **in-memory per JVM**. Instance A's circuit can be open while Instance B's is closed. | Replace with Resilience4j Redis backend, or use a service mesh (Istio) for circuit breaking at the infrastructure layer. |
| **Kafka consumer group** | `spring.kafka.consumer.group-id` must be the same across all instances of a service so Kafka assigns partitions across them. Currently this may not be set correctly for horizontal scaling. | Verify `group-id` is set per-service (e.g., `ledger-service`), not per-instance. Kafka will distribute the 3 partitions across up to 3 consumer instances. |

#### 4b. Leader Election / Scheduled Tasks

| Problem | Current State | Fix |
|---|---|---|
| **Idempotency key cleanup** | Expired idempotency keys accumulate forever. There is no scheduled cleanup job. | Add `@Scheduled` cleanup task. But with multiple instances, every instance runs it simultaneously → duplicate deletes, resource waste. | Use ShedLock or Quartz with DB-backed leader election to ensure only one instance runs scheduled tasks at a time. |
| **Reconciliation job** | Reconciliation is triggered via HTTP. With multiple instances, two callers could trigger concurrent full reconciliations. | Add a distributed lock (Redisson `RLock`) around the reconciliation scan. |

#### 4c. Database Connection Under Scale

| Problem | Current State | Fix |
|---|---|---|
| **N instances × 20 connections = 400 connections** | With 20 instances of ledger-service each holding 20 HikariCP connections, you exhaust PostgreSQL's `max_connections=100` immediately. | PgBouncer connection pooler in front of PostgreSQL. Services connect to PgBouncer, not Postgres directly. PgBouncer maintains a small real connection pool to Postgres. |
| **No read replicas** | All reads and writes go to the same Postgres instance. Heavy reporting/reconciliation queries block OLTP. | PostgreSQL streaming replication → read replica. Route `@Transactional(readOnly = true)` queries to the replica via Spring's `AbstractRoutingDataSource`. |
| **No database sharding** | All ledger data in one DB. At scale (millions of accounts), this becomes a bottleneck. | Account-based sharding key, or move to a distributed DB (CockroachDB, YugabyteDB) for horizontal scale. |

#### 4d. Service Mesh / Load Balancing

| Missing | What to Add |
|---|---|
| No load balancer in front of services | Kubernetes Ingress (NGINX or Traefik) replacing the current single docker-compose mapping |
| Eureka client-side load balancing works for 1 gateway | Spring Cloud LoadBalancer already in gateway — this scales, but needs Kubernetes to schedule replicas |
| No pod disruption budgets | Ensure rolling updates don't take all instances offline simultaneously |

**Interview answer:** *"The main multi-instance risk is the Resilience4j circuit breaker being in-process memory — each instance makes its own open/close decision independently. At 10 instances, half could be open and half closed, giving inconsistent behavior to clients. The fix is either externalizing circuit state to Redis or delegating circuit breaking to a service mesh like Istio. The rate limiter and cache are already Redis-backed so they're consistent across instances."*

---

### 5. Concurrency & Thread Safety *(Deep Dive)*

| Gap | Detail | Fix |
|---|---|---|
| **Race condition on balance check + debit** | `postTransfer()` reads source balance, checks sufficiency, then inserts journal entries — these are two separate operations even inside a `@Transactional` block. With `READ COMMITTED` isolation (Postgres default), another concurrent transaction can drain the account between the check and the write. | Use `SELECT ... FOR UPDATE` on the source account row (pessimistic lock), or rely on optimistic locking + retry on `OptimisticLockException`. Currently optimistic locking is declared but the service code doesn't explicitly handle `ObjectOptimisticLockingFailureException` with a retry. |
| **No retry on optimistic lock failure** | `@Version` raises `OptimisticLockingFailureException` on conflict, but callers get a raw 500. | Add `@Retryable(value = OptimisticLockingFailureException.class, maxAttempts = 3)` on `postTransfer()`. |
| **Kafka consumer thread safety** | `listener.concurrency: 3` means 3 threads share the same Spring bean instances. Confirm all service beans accessed by the consumer are stateless or thread-safe. | Audit `@Service` beans used in `TransactionEventListener` — they must have no mutable instance fields. |
| **`System.out.println` in JWT filter** | Not a thread safety issue, but `System.out` is synchronized and is a throughput bottleneck under concurrent load. | Replace all `System.out.println` with `log.error(...)` (Slf4j). |
| **Virtual threads (Java 21)** | Kafka consumers and Tomcat threads are platform threads. Java 21 virtual threads can handle 10x more concurrent I/O-bound tasks with the same heap. | Switch to Spring Boot 3.2+ virtual thread executor: `spring.threads.virtual.enabled=true`. |
| **CompletableFuture for parallel lookups** | Some operations (e.g., validating both source and destination accounts before a transfer) do sequential DB calls that could be parallelized. | `CompletableFuture.allOf(fetchSource, fetchDest)` to run account lookups in parallel before the transaction begins. |
| **Kafka consumer lag under burst** | With 3 partitions and `listener.concurrency: 3`, max throughput is 3 parallel messages. Under high transaction volume, consumer lag grows. | Scale to more Kafka partitions (e.g., 12) and more consumer instances. Monitor lag with `kafka-consumer-groups.sh --describe`. |

**Interview answer:** *"The most dangerous concurrency gap is the TOCTOU (time-of-check-time-of-use) race in `postTransfer`. The balance check and the journal entry insert are two steps. If two threads both pass the balance check at the same moment, both will write journal entries and the account goes negative. The fix is either a database-level row lock (`SELECT FOR UPDATE`) or catching `OptimisticLockingFailureException` and retrying with fresh state."*

---

### 6. Testing *(Significant Gap)*

| Missing | Impact | What to Add |
|---|---|---|
| **No integration tests** | `CacheLoadTest` uses `@SpringBootTest` but mocks the DB. The full request path (HTTP → Gateway → Service → DB → Kafka → Notification) is never tested end-to-end. | Testcontainers: spin up real Postgres, Redis, Kafka in Docker during tests. Test the full slice. |
| **No auth-service unit tests** | JWT token generation, BCrypt verification, refresh token logic — none of it has test coverage. | Unit tests for `JwtService`, `AuthService` with mocked repositories. |
| **No contract tests** | Gateway and ledger-service have an implicit API contract (request/response shapes). If ledger changes its API, gateway breaks silently. | Spring Cloud Contract or Pact for consumer-driven contract testing. |
| **No performance / load tests** | No benchmark for throughput, latency under concurrent load, or how the system degrades at limits. | Gatling or k6 script simulating 1000 concurrent transfers. Assert P99 latency < 500ms. |
| **No chaos tests** | No verification that circuit breakers, retries, and fallbacks actually fire under real failure. | Chaos Monkey for Spring Boot (`chaos-monkey-spring-boot`), or Toxiproxy to inject network latency/drops. |
| **No mutation testing** | Existing tests may pass even with bugs in business logic if assertions are weak. | PITest (mutation testing) to measure true test effectiveness. |
| **Low branch coverage** | Only ~14 test methods total across 5 services. | Aim for 80%+ line coverage on ledger-service (the core domain). |

---

### 7. API Design & Developer Experience

| Missing | Fix |
|---|---|
| **No OpenAPI / Swagger UI on gateway** | Individual services have Springdoc OpenAPI but the gateway doesn't aggregate them. Clients can't explore the full API from one place. | Spring Cloud Gateway + Springdoc aggregation: forward `/v3/api-docs/{service}` per service, expose unified Swagger UI. |
| **No API versioning strategy** | All routes are `/api/accounts`, `/api/transactions` with no version. A breaking change breaks all clients. | URL versioning (`/v1/`, `/v2/`) or header-based (`Accept: application/vnd.finledger.v2+json`). |
| **No pagination on all list endpoints** | Some list endpoints return unbounded results. With millions of transactions, this is an OOM risk. | Enforce `Pageable` on every collection endpoint. Max page size 1000. |
| **No hypermedia (HATEOAS)** | Clients hardcode URLs. | Spring HATEOAS: response includes links to related resources (`self`, `transactions`, `balance`). |
| **Bulk operations missing** | No way to create multiple accounts or query multiple balances in one call. Clients make N calls instead of 1. | `POST /api/accounts/batch`, `GET /api/accounts/balances?ids=a,b,c`. |
| **Idempotency key TTL cleanup** | Expired keys accumulate in the `idempotency_keys` table forever. | Scheduled cleanup job + index on `expires_at` (already indexed, just needs the job). |

---

### 8. Resilience Patterns (Beyond What's Built)

| Missing Pattern | What It Solves | How to Add |
|---|---|---|
| **Bulkhead** | One slow downstream (e.g., notification-service) consumes all gateway threads, starving other routes. | Resilience4j `@Bulkhead` with thread pool isolation per downstream service. |
| **Saga pattern** | A transfer that posts to DB but fails to publish to Kafka leaves the system in a partial state (DB committed, no notification sent). | Transactional Outbox pattern: write Kafka event to a DB `outbox` table inside the same DB transaction. A separate relay process reads the outbox and publishes to Kafka. Guarantees exactly-once delivery. |
| **Event sourcing** | Current state: only latest state stored. You cannot reconstruct history or do point-in-time account state. | Store all events (TransferRequested, TransferSettled, TransferReversed) in an event log. State is derived by replaying events. |
| **Dead letter monitoring** | DLT handler logs an alert but doesn't trigger any automation. Messages sit in DLT forever. | Periodic DLT consumer that retries, pages on-call (PagerDuty), and moves to manual review queue after N days. |
| **Graceful shutdown** | If a pod is killed mid-transaction, in-flight requests fail with a connection reset. | `spring.lifecycle.timeout-per-shutdown-phase=30s` and `server.shutdown=graceful`. Kubernetes `terminationGracePeriodSeconds` to match. |

---

### 9. Kubernetes & Production Infrastructure

| Missing | What It Enables |
|---|---|
| **Kubernetes manifests (Deployment, Service, Ingress, HPA)** | Declarative deployment, rolling updates, auto-healing |
| **Horizontal Pod Autoscaler (HPA)** | Scale from 2 to 20 replicas based on CPU/RPS — currently you can only scale manually |
| **Helm chart** | Parameterized deployments for dev/staging/prod environments |
| **Pod Disruption Budgets** | Guarantee minimum available replicas during rolling updates |
| **Kubernetes Secrets / External Secrets Operator** | Mount Vault secrets as env vars; rotate without redeployment |
| **Resource requests vs limits** | Docker Compose has limits but K8s manifests need `requests` for scheduling and `limits` for protection |
| **Liveness vs Readiness probes** | Actuator endpoints exist but no K8s probe config — pods marked ready before Spring context is fully initialized |
| **NetworkPolicy** | Restrict which pods can talk to which. Ledger-service should NOT be directly reachable from outside the cluster — only via gateway. |
| **PersistentVolumeClaims for Postgres and Redis** | Without PVCs, data is lost when pods restart |
| **Kafka cluster (3+ brokers)** | Single Kafka broker is a single point of failure. Production needs 3 brokers minimum, replication factor 3. |

---

### 10. Data Integrity & Financial Correctness

| Gap | Detail | Fix |
|---|---|---|
| **No reversal idempotency** | Transaction reversal is defined in the enum but the reversal endpoint and logic aren't implemented. | Implement `POST /api/transactions/{id}/reverse` with its own idempotency key. |
| **Amount precision** | `DECIMAL(19,2)` — correct for most currencies but not cryptocurrency (8 decimal places) or JPY (0 decimals). | Use `DECIMAL(30,8)` and carry currency-specific scale in the domain layer. |
| **Currency validation** | No check that source and destination accounts share the same currency before posting a transfer. A USD→EUR transfer would post without exchange rate conversion. | Validate currency match or implement FX conversion with rate lookup. |
| **No account balance floor enforcement at DB level** | Insufficient balance is checked in application code, but there's no DB constraint preventing negative balances. If code is bypassed (direct DB access, bug), accounts go negative. | DB CHECK constraint: `balance >= 0` (requires a materialized balance column, or a trigger on `journal_entries`). |
| **Soft delete vs hard delete** | No soft delete on accounts. Deleting an account with journal entries would violate FK constraints — it would just throw. | Add `deleted_at` timestamp. Never hard-delete financial records. |

---

## Quick Reference: Interview Cheat Sheet

### "What would you do differently in production?"

1. **Distributed tracing** — Jaeger/Zipkin + Micrometer to trace requests across all 5 services
2. **Transactional Outbox** — guarantee Kafka events are published even if the broker is down at commit time
3. **Circuit breaker state in Redis** — not per-JVM, so all instances share the same open/close state
4. **PgBouncer** — connection pooler so N instances don't exhaust PostgreSQL's connection limit
5. **CI/CD pipeline** — automated build, scan, test, and deploy on every commit
6. **Kubernetes with HPA** — replace Docker Compose, auto-scale on load
7. **SELECT FOR UPDATE on balance check** — eliminate the TOCTOU race in `postTransfer`
8. **Vault for secrets** — no hardcoded JWT secret in `application.yml`
9. **Audit log** — append-only table for every state change (regulatory requirement)
10. **DLT monitoring** — automated retry and alerting for dead-lettered Kafka messages

### "How does it scale to 1 million transactions/day?"

- Kafka with 12 partitions → 12 parallel consumer threads across 4 ledger-service instances
- PgBouncer → each instance uses 5 connections to PgBouncer → PgBouncer maintains 20 to Postgres
- Redis caching absorbs 90%+ of balance reads (cache hit ratio > 0.9 target)
- Read replica routes all `readOnly` queries (reconciliation, search) off the primary
- HPA scales ledger-service from 2 to 20 pods under load
- Gateway rate limiter protects downstream from being overwhelmed

### "What concurrency bugs exist?"

1. **TOCTOU in `postTransfer`** — balance check + journal write are not atomic at DB level (see Section 5)
2. **Resilience4j state is per-JVM** — inconsistent circuit state across instances
3. **No retry on `OptimisticLockingFailureException`** — high-concurrency writes silently return 500

### "Walk me through your Kafka setup"

- Producer: `acks=all`, `retries=3`, `linger.ms=10` (micro-batching), transaction ID as message key for ordering
- Consumer: `concurrency=3`, `max-poll-records=100`, `@RetryableTopic` with 3 retries + 1s delay
- On exhaustion: DLT (`transaction-settled.DLT`), `@DltHandler` logs critical alert
- Missing: Outbox pattern, DLT monitoring, consumer lag alerting, exactly-once semantics

---

*File generated: 2026-07-15. Interview: 2026-07-17.*
