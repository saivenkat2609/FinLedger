# FinLedger — Interview Questions & Answers

> Focus: High-level questions an interviewer asks when they haven't read your code.
> They want to know *what you built*, *why you made those choices*, and *how you thought about problems*.

---

## 1. Project Overview

**Q: Tell me about this project. What does it do?**

FinLedger is a double-entry ledger system — the kind of backend that powers the financial core of a banking or fintech application. It lets you create accounts, transfer money between them, and run reconciliation reports to verify the books balance. I built it as a microservices system with five services: an API gateway, an auth service, the core ledger service, a notification service, and a service registry. The whole thing runs on PostgreSQL, Redis, and Kafka.

---

**Q: Why did you pick a financial ledger as your project?**

Financial systems are one of the hardest domains to get right — you're dealing with money, so correctness matters more than in most applications. It forced me to think about things like double-spend prevention, atomicity, idempotency, and audit trails. A CRUD app doesn't teach you those lessons. The ledger also touches a wide range of technologies naturally — caching, messaging, auth, circuit breakers — without feeling forced.

---

**Q: What is double-entry bookkeeping and why does your system use it?**

In double-entry bookkeeping every transaction has two sides — money leaving one account (a debit) and the same amount arriving in another (a credit). The rule is that debits must always equal credits. This means money can never appear or disappear — if the numbers don't balance, something went wrong. I use it because it's the standard for any serious financial system and it gives you a built-in correctness check: if your totals don't balance, you know there's a bug or a fraud.

---

**Q: What are the main features of this project?**

- User registration and login with JWT-based authentication
- Creating and managing financial accounts
- Transferring money between accounts with idempotency (safe to retry without double-charging)
- A full journal entry trail for every transaction
- Account balance caching for fast reads
- Reconciliation reports to verify the ledger balances for a given date range
- Event-driven notifications via Kafka when a transaction settles
- Circuit breakers and rate limiting at the gateway level
- Structured JSON logging with correlation IDs across all services

---

## 2. Architecture & Technology Choices

**Q: Why microservices? Why not just build a monolith?**

Microservices made sense here because I wanted to demonstrate production patterns — service discovery, circuit breaking, inter-service communication, independent deployability. That said, for a project this size a modular monolith would actually be simpler to run. The honest trade-off is: microservices give you independent scaling and fault isolation, but they add operational complexity, distributed debugging overhead, and network failure scenarios you don't have with a monolith. I chose microservices deliberately to learn those patterns.

---

**Q: Walk me through your microservices. Why did you split them this way?**

- **gateway-service**: single entry point for all clients. Handles JWT validation, rate limiting, circuit breaking, and routing. Everything that applies to every request lives here so the downstream services don't repeat it.
- **auth-service**: handles only identity — register, login, token refresh, token validation. Separated because auth logic changes independently from business logic, and you might swap auth providers (Keycloak, Auth0) without touching the ledger.
- **ledger-service**: the core domain — accounts, transactions, journal entries, reconciliation. This is where the business rules live. It's the most important service and the one that should be the most stable.
- **notification-service**: consumes events from Kafka and sends notifications. Separated because notifications are a background concern — if it's slow or down, it shouldn't affect the transaction flow.
- **eureka-service**: service registry. Lets services find each other by name instead of hardcoded IPs.

---

**Q: Why Spring Boot? Why not something like Node.js or Go?**

Spring Boot is the standard in enterprise Java backends. It has a mature ecosystem for everything this project needs: Spring Security for auth, Spring Data JPA for the DB layer, Spring Cloud Gateway for the API gateway, Resilience4j for circuit breakers, Spring Kafka for messaging. The autoconfiguration reduces boilerplate significantly. I chose Java because the financial domain heavily uses it in the real world, and because the type system and transaction management in Spring are well-suited for correctness-critical code.

---

**Q: Why PostgreSQL? What made you choose it over MySQL or a NoSQL database?**

PostgreSQL gives you ACID transactions, which are non-negotiable for financial data — I need the debit and credit journal entries to either both commit or both fail, with no partial state. It has excellent support for decimal arithmetic (which matters for money — floating point is not acceptable), row-level locking, and a strong foreign key model. I wouldn't use a NoSQL database like MongoDB for the ledger because financial data has a fixed relational structure and you need transactional guarantees that NoSQL databases either don't support or add significant complexity to achieve.

---

**Q: Why Redis? What are you using it for?**

Two things. First, caching account balances — computing a balance means summing all journal entries for an account, which gets slow as history grows. Redis stores the result with a 5-minute TTL so repeated balance reads don't hit the database. Second, rate limiting — the gateway uses Redis to count requests per IP per minute window. Redis is the right choice for both because it's in-memory (microsecond reads), supports TTL natively, and is shared across all service instances so the rate limit and cache are consistent even when you're running multiple copies of a service.

---

**Q: Why Kafka? Why not just make a direct HTTP call from ledger-service to notification-service?**

If I called notification-service directly over HTTP, the transaction flow would be coupled to whether notifications are working. If the notification service is slow, the user's transfer takes longer. If it's down, the transfer fails. Kafka decouples them — ledger-service publishes an event and moves on; notification-service processes it whenever it's ready. Events are also durable — if notification-service is down for 10 minutes, it catches up when it comes back. The tradeoff is eventual consistency: the notification arrives shortly after, not instantly.

---

**Q: Why do you have a separate API gateway instead of exposing each service directly?**

The gateway is a single entry point that handles cross-cutting concerns once: JWT validation, rate limiting, circuit breaking, SSL termination, routing. Without it, every service would have to implement JWT validation and rate limiting independently, and clients would need to know the address of every service. The gateway also lets you change your internal service topology without clients noticing — you can split or merge services as long as the external API stays the same.

---

**Q: Why Eureka for service discovery? What does it give you?**

In a containerized environment, service instances get dynamic IP addresses and ports. Eureka lets each service register itself by name on startup and lets other services discover it by that name (e.g. `ledger-service`) without hardcoding any IPs. When you scale up — say, three instances of ledger-service — the gateway automatically discovers all three and load-balances across them. Without service discovery, you'd have to update configs every time an instance's address changed.

---

## 3. Authentication & Security

**Q: How does authentication work in your system?**

The user sends their username and password to the auth-service. If valid, auth-service returns two tokens: a short-lived access token (15 minutes) and a long-lived refresh token (7 days). For every subsequent request, the client sends the access token in the `Authorization` header. The gateway intercepts it, calls auth-service to validate the token, and if valid, strips the token and forwards the user's ID and role as trusted headers to the downstream service. The downstream service doesn't need to know anything about JWT — it just reads those headers.

---

**Q: Why do you have two tokens — an access token and a refresh token? Why not just one?**

If the access token is stolen, you want it to expire quickly — that's why it's only 15 minutes. But asking users to re-enter their password every 15 minutes is terrible UX. The refresh token is used only to get a new access token silently in the background. The key difference is that refresh tokens are stored in the database, so they can be revoked — if you suspect a user's session is compromised, you delete their refresh token and they're logged out. Access tokens are stateless JWTs — you can't revoke them before they expire, which is why you keep them short-lived.

---

**Q: What is JWT and why use it instead of sessions?**

A JWT is a self-contained token — it carries the user's ID, role, and expiry encoded and cryptographically signed. Any service that has the secret key can verify it without calling a database. Sessions, by contrast, store the session ID in a database, so every request requires a DB lookup to validate. JWT is better for microservices because each service can validate tokens independently without a shared session store. The downside is that you can't invalidate a JWT before it expires — sessions can be deleted immediately.

---

**Q: How do you prevent someone from calling ledger-service directly, bypassing the gateway?**

In this project, ledger-service also has its own JWT filter — it validates the token again independently. In a production environment you'd add network policies so ledger-service only accepts connections from the gateway (private network, firewall rules, or Kubernetes NetworkPolicy). The JWT re-validation is a defense-in-depth measure — even if someone reaches the service directly, they still need a valid token.

---

**Q: What security concerns did you think about when building this?**

JWT secret management — the secret must never be in source code. Rate limiting at the gateway to prevent brute-force and DDoS. Idempotency keys to prevent duplicate transactions. Input validation on amounts (can't transfer negative or zero). Authorization — users should only access their own accounts (though this is partially implemented). HTTPS for all external communication. Not logging sensitive data like passwords or full card numbers.

---

## 4. Core Features — Transactions & Ledger

**Q: How does a money transfer work end-to-end in your system?**

The client sends a POST to `/transactions/transfer` with source account, destination account, amount, and an idempotency key. The gateway validates the JWT and routes the request to ledger-service. Ledger-service first checks if it's seen the idempotency key before — if yes, it returns the existing result without re-running. If new, it checks the source account has sufficient balance, creates two journal entries (one DEBIT on source, one CREDIT on destination) in a single database transaction, marks the transaction as SETTLED, invalidates the cached balance for both accounts, and publishes a `TransactionSettledEvent` to Kafka. The notification-service picks it up and sends a notification.

---

**Q: What is an idempotency key and why does your API require one?**

An idempotency key is a unique ID the client generates for each operation, like a UUID. If the client's network drops after sending the request but before getting the response, it doesn't know if the transfer went through. Without an idempotency key, retrying would create a duplicate transfer — charging the user twice. With an idempotency key, the server checks if it's seen that key before. If it has, it returns the same response without re-running the transaction. The client can safely retry without fear of duplicates. This is essential for any payment API.

---

**Q: Why do you create journal entries instead of just updating account balances directly?**

Journal entries are an immutable audit trail. Once a transaction commits, you have a permanent record of exactly what moved, when, and why. If you only update account balances, you lose that history — you'd know an account has £500 but not how it got there. Journal entries also enable reconciliation: you can sum all debits and credits for any date range and verify they balance. In regulated financial systems, you're legally required to maintain this kind of trail.

---

**Q: How do you make sure a transfer is atomic — that either both sides succeed or neither does?**

Both journal entries (debit and credit) are created inside a single `@Transactional` method. If anything goes wrong — a database error, an exception — Spring rolls back the entire transaction and neither entry persists. The account balances (derived from journal entries) therefore never get into an inconsistent state where one side posted but the other didn't.

---

**Q: What happens if someone tries to transfer more than their account balance?**

Before creating the journal entries, I query the sum of all journal entries for the source account to get its current balance. If the balance is less than the transfer amount, I throw an `InsufficientFundsException` and return a 400 Bad Request. The transaction is never created.

---

**Q: What is reconciliation and how did you implement it?**

Reconciliation is the process of verifying that the books are correct — that the total of all debits equals the total of all credits for a given period. I implemented a reconciliation report that takes a date range, sums all debit journal entries and all credit journal entries for transactions that settled in that range, and checks if they're equal. I also have a discrepancy finder that compares each account's database-computed balance against its cached balance in Redis to catch any inconsistencies between the cache and the source of truth.

---

## 5. Caching

**Q: What did you cache and why?**

Account balances. Computing a balance requires summing all journal entries for an account — as transaction history grows, this query gets slower. I cache the result in Redis with a 5-minute TTL using Spring's `@Cacheable` annotation. Whenever a transaction posts to an account, I evict that account's cached balance with `@CacheEvict` so the next read recalculates from the database.

---

**Q: Why did you use Redis for caching instead of an in-memory cache like Caffeine?**

In-memory caches like Caffeine are per-instance — if you run three instances of ledger-service, each has its own cache with potentially different values. One instance might serve a stale balance while another serves the fresh one. Redis is a shared external cache — all instances read and write the same values, so the cache is consistent regardless of which instance handles the request. For a financial system where balance accuracy matters, a shared cache is the right choice.

---

**Q: What is cache eviction and when does it happen?**

Cache eviction is removing a cached entry because it's no longer valid. I evict the cached balance for an account immediately after any transaction posts to it. This ensures the next read recomputes from the database rather than returning a stale value. Without eviction, someone could post a transfer and immediately read a balance that doesn't reflect it. The TTL (5 minutes) is a safety net — even if eviction fails for some reason, the cache entry expires and the next read is fresh.

---

**Q: What are the risks of caching in a financial system?**

The main risk is serving a stale balance. If someone reads a cached balance, posts a transfer, and the cache isn't evicted properly, a subsequent read returns the pre-transfer balance — which could allow an overdraft. I mitigate this by evicting on every write. A second risk is cache stampede — if the cache expires for a popular account, many requests simultaneously hit the database. A third risk is Redis going down — my application falls back to reading directly from the database, so it degrades gracefully but with higher latency.

---

## 6. Circuit Breakers & Resilience

**Q: What is a circuit breaker and why did you add one?**

A circuit breaker is a pattern that prevents an application from repeatedly calling a service that's failing. In a microservices system, if ledger-service is down and every request tries to connect to it, you waste threads waiting for connections that will time out — which can bring down the gateway too (cascading failure). The circuit breaker monitors the failure rate. When too many calls fail, it trips open and immediately returns a fallback response (503 Service Unavailable) without attempting the call. After a wait period, it lets one probe request through. If that succeeds, it closes again.

---

**Q: What's the difference between a circuit breaker and a retry?**

Retry is for transient failures — a brief network glitch that resolves quickly. You retry two or three times with a short delay and usually succeed. Circuit breaker is for sustained failures — a service that's been down for minutes. Retrying into a failed service wastes time and resources; the circuit breaker stops the attempt immediately. You typically use both: retry handles blips, circuit breaker handles outages. The key rule is never retry non-idempotent operations (like a money transfer) without idempotency guarantees.

---

**Q: What does your system do when the ledger-service is down? Does the whole thing fail?**

The gateway's circuit breaker trips open and the gateway returns a 503 with a fallback response — a clean error message telling the client the service is temporarily unavailable. The gateway itself stays up and healthy; it just can't forward requests to ledger-service. Auth endpoints (which don't need ledger-service) continue working. When ledger-service recovers, the circuit breaker detects it through a probe request and starts routing again automatically.

---

**Q: What is rate limiting and why did you implement it at the gateway?**

Rate limiting caps how many requests a client can make in a time window. Without it, a single client (or a bot) can flood the system with requests and starve other users. I implemented it at the gateway because that's the single entry point — it's the right place to enforce limits before requests even reach the business services. Using Redis means the limit is shared across all gateway instances, so scaling horizontally doesn't give clients a way around the limit.

---

## 7. Kafka & Event-Driven Design

**Q: Why did you use an event-driven approach for notifications?**

The transaction flow (creating journal entries, settling the transaction) is synchronous and time-sensitive — the user is waiting for a response. Sending a notification (email, push, SMS) is slow and unreliable — third-party services have variable latency and occasional outages. If I did it synchronously, a slow notification provider would slow down every transfer. By publishing an event to Kafka and having the notification-service process it asynchronously, the transaction completes in milliseconds and the notification arrives seconds later. The user experience is much better.

---

**Q: What happens if the notification-service is down when a transaction settles?**

Kafka retains messages on disk. When notification-service comes back up, it picks up from where it left off — it processes all the events it missed. This is one of Kafka's key advantages over direct HTTP calls: the notification-service can be down for hours and no events are lost. This wouldn't be possible with a synchronous HTTP call, which would fail immediately and lose the notification.

---

**Q: What is a consumer group in Kafka?**

A consumer group is a set of consumers that share the work of reading a topic. Each message goes to exactly one consumer in the group. This lets you scale horizontally — run three instances of notification-service and each handles a third of the messages in parallel. If one instance goes down, Kafka redistributes its partitions to the remaining instances. All instances share the same group ID so they don't duplicate each other's work.

---

**Q: What is a Dead Letter Topic?**

When a message fails processing after all retries — say the message is malformed and keeps throwing an exception — Kafka would normally block that partition forever waiting for it to succeed. A Dead Letter Topic (DLT) is where those permanently-failed messages go instead of blocking. My notification-service retries a failed message four times, and if it still fails, the message lands in the DLT. From there, an engineer can inspect what went wrong, fix the bug, and replay the message manually. Without a DLT, failed messages either block processing or are silently dropped.

---

## 8. Logging & Observability

**Q: How do you trace a request across multiple services in your logs?**

Every incoming request gets a correlation ID — a unique UUID in the `X-Correlation-ID` header. Each service reads this header and puts it into the logging context (MDC — Mapped Diagnostic Context). Every log line that service produces while handling that request includes the correlation ID. The gateway also forwards this header downstream, so all five services log the same ID for the same user request. To debug an issue, I search my log aggregator for a specific correlation ID and immediately see the full request journey across all services.

---

**Q: What is structured logging?**

Structured logging means writing logs as JSON objects instead of plain text strings. Instead of `"2024-01-15 ERROR Transfer failed for user 123"`, you get `{"timestamp": "2024-01-15T10:23:45Z", "level": "ERROR", "correlationId": "abc-123", "userId": "user-123", "message": "Transfer failed"}`. The advantage is that log aggregation tools (like the ELK stack or Datadog) can index, search, and alert on individual fields — you can query `level=ERROR AND service=ledger-service` or `correlationId=abc-123` directly, rather than using fragile regex patterns on plain text.

---

**Q: What would you add to improve observability in a production version?**

Distributed tracing with something like Zipkin or Jaeger — this goes beyond correlation IDs by automatically timing every HTTP call and DB query, giving you a visual flame chart of a request's journey. Metrics with Prometheus and dashboards in Grafana — so you can see request rates, error rates, latency percentiles, and circuit breaker states at a glance. Health check endpoints with meaningful status (not just "up" but "up, DB connected, Redis connected, Kafka connected"). Alerting when error rates spike or circuit breakers open.

---

## 9. Database & Data Design

**Q: How did you handle database schema migrations?**

I used Liquibase. Every schema change is a versioned changelog file. When the application starts, Liquibase checks which changesets have already been applied and runs only the new ones. This means every environment — local, staging, production — always has the same schema, and the migration history is in source control alongside the code. It also forces you to think about rollbacks and write them explicitly, rather than dropping and recreating tables manually.

---

**Q: Why do you store journal entries instead of just updating a balance field on the account?**

The balance is a derived value — it's the sum of all movements on that account. If you store it as a column, you have two sources of truth that can drift apart if a bug skips the update. Journal entries are immutable facts — once written, they're never changed. The current balance is always computable from them. This also gives you a full audit trail, which is required in regulated financial systems. The tradeoff is read performance for the balance query, which I address with Redis caching.

---

**Q: How do you handle concurrent transactions on the same account?**

Each journal entry and transaction entity has a `@Version` field for optimistic locking — if two requests try to modify the same entity simultaneously, one will get a conflict and retry. The database also has a unique constraint on idempotency keys to prevent duplicate transactions at the storage level. In a more robust implementation, I'd also add a `SELECT FOR UPDATE` on the balance check to prevent two concurrent transfers from both reading the same balance and both passing the sufficiency check — a classic double-spend scenario.

---

**Q: Why PostgreSQL over a NoSQL database?**

The ledger data is highly relational — accounts link to journal entries which link to transactions, with foreign key constraints that enforce referential integrity. ACID transactions are mandatory — I cannot have half a transfer persist. The data model is fixed and well-understood, so the flexibility of a schema-less database adds complexity without benefit. PostgreSQL's support for decimal types, row-level locking, and mature transactional semantics makes it the right tool for this domain.

---

## 10. Deployment & Infrastructure

**Q: How do you run this project?**

With Docker Compose. A single `docker compose up` starts all five application services plus PostgreSQL, Redis, Kafka, Zookeeper, and Kafdrop (a Kafka UI). Liquibase runs on startup and applies any pending migrations. All inter-service communication happens over a Docker bridge network using service names (so `ledger-service` just calls `http://auth-service:8081` internally). The gateway is the only service that exposes a port to the host machine.

---

**Q: What would you change to deploy this to production on Kubernetes?**

Replace Docker Compose with Kubernetes manifests — a Deployment for each service, a Service for internal DNS, ConfigMaps for non-sensitive config, and Secrets for JWT keys and database passwords. Eureka becomes redundant because Kubernetes provides its own DNS-based service discovery. Add liveness probes (is the app alive?) and readiness probes (is it ready to serve traffic?) so Kubernetes knows when to restart a pod or when to stop sending it traffic during a rolling deploy. Add Horizontal Pod Autoscaling (HPA) to scale services based on CPU or request rate. Add resource requests and limits so no single service can starve others.

---

**Q: How would you scale this system if traffic grew 10x?**

Gateway is stateless — scale it horizontally behind a load balancer. Ledger-service is stateless — scale it horizontally; Eureka distributes traffic across instances. The database becomes the bottleneck — add a read replica for balance queries and reconciliation reports; only writes go to the primary. Redis already handles the balance cache, which reduces DB load significantly. Kafka partitions can be increased to allow more notification-service consumers in parallel. Long-term, if a single PostgreSQL instance can't handle the write throughput, you'd shard accounts across multiple database nodes, though that significantly increases complexity.

---

**Q: What environment variables or configuration does this project need to run?**

Database URL and credentials, Redis host and port, Kafka bootstrap server address, JWT secret (the same one across auth-service and ledger-service), and service discovery URL (Eureka's address). In Docker Compose these are all injected via environment variables. The applications have sensible defaults for local development but production values should never be in source code — they should come from a secrets manager like HashiCorp Vault or AWS Secrets Manager.

---

## 11. Trade-offs & Design Decisions

**Q: What was the hardest problem you solved in this project?**

Ensuring atomicity and correctness in the transaction flow. Money transfers require two journal entries to always succeed or always fail together, idempotency so retries don't double-charge, and cache invalidation timed correctly with the DB commit. Getting all three right simultaneously — especially making sure the cache is evicted after the commit, not before — required careful use of Spring's transactional context and a good understanding of how `@Transactional` and `@CacheEvict` interact.

---

**Q: If you were starting this project over, what would you do differently?**

Start with a modular monolith and extract services only when there's a real reason to — not upfront. Add integration tests from the beginning using Testcontainers, because testing microservices interactions is hard without them. Implement the Outbox Pattern from the start for Kafka publishing, instead of the direct publish approach which loses events if Kafka is down. And set up proper observability (Prometheus + Grafana) early — debugging distributed systems without metrics is painful.

---

**Q: What are the weaknesses of your current implementation?**

The Kafka event publishing isn't guaranteed — if Kafka is down at the moment of a transaction, the event is lost (the Outbox Pattern would fix this). The balance check doesn't use a database lock, which means two simultaneous transfers on the same account could theoretically both pass the balance check (double-spend). Test coverage is minimal — there are no unit tests for the core business logic. And secrets are currently managed via environment variables in Docker Compose, which isn't suitable for production where you'd use a proper secrets manager.

---

**Q: Why not use a message queue like RabbitMQ instead of Kafka?**

Kafka is better suited here for two reasons: replay and scale. Kafka retains messages on disk for a configurable retention period, so you can replay events if you add a new consumer or need to reprocess history. RabbitMQ deletes messages once consumed. For a financial system, being able to replay and audit the event history is valuable. Kafka also handles high-throughput partitioned logs better at scale. RabbitMQ is a better fit for task queues and lower-throughput work distribution where you don't need replay.

---

**Q: How would you add a new feature — say, transaction limits per account?**

I'd add a `daily_limit` field to the Account entity and a migration for the column. In `TransactionService.postTransfer()`, before the balance check, I'd query the sum of today's transactions for the source account and compare it against the limit. If exceeded, I'd throw a `TransactionLimitExceededException` and return a 400. I'd cache the daily total in Redis with a TTL set to end of day to avoid a DB query on every transfer. I'd add a new test case covering the limit enforcement and update the API documentation. No other services would need to change.

---

**Q: How do you ensure the system is consistent after a crash or restart?**

Database transactions ensure atomic writes — a crash mid-transaction rolls back completely, leaving no partial state. Liquibase applies pending migrations idempotently on startup. Kafka consumers track their offset — on restart they continue from where they left off, so no events are lost. Redis is a cache, not a source of truth — if it's lost, the data is rebuilt from the database on the next read. The one gap is the Kafka event publish: if the application crashes after committing the DB transaction but before publishing to Kafka, the event is lost. The Outbox Pattern solves this by making the event publish part of the same DB transaction.

---

## 12. API Design & Error Handling

**Q: What does your API look like? What endpoints do you expose?**

The API is RESTful, versioned under `/api/v1`, and everything goes through the gateway on port 8080. The main endpoint groups are:
- `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh` — identity
- `POST /accounts`, `GET /accounts/{id}`, `GET /accounts/{id}/balance` — account management
- `POST /transactions/transfer`, `GET /transactions/{id}`, `GET /transactions/account/{id}` — transfers and history
- `GET /reconciliation/report?from=&to=`, `GET /reconciliation/discrepancies` — financial reporting

Auth endpoints are public. Everything else requires a valid JWT.

---

**Q: How do you handle errors in your API? What happens when something goes wrong?**

I have a global exception handler (`@RestControllerAdvice`) that catches exceptions and maps them to consistent JSON error responses with a timestamp, error code, and message. Specific exceptions map to specific HTTP status codes: `InsufficientFundsException` → 400, `AccountNotFoundException` → 404, `DuplicateTransactionException` → 409, auth failures → 401, validation errors → 422. Unhandled exceptions fall through to a generic 500 handler. This way every error response has the same shape regardless of which service handled it, and clients can rely on the status codes to drive their error-handling logic.

---

**Q: Why did you use REST and not GraphQL or gRPC?**

REST is the right default for a public-facing financial API — it's universally understood, works with any HTTP client, and maps naturally to the resource model (accounts, transactions, journal entries). GraphQL would be overkill here; the data model isn't deeply nested and I don't have a frontend with variable data needs. gRPC would be worth considering for internal service-to-service calls (binary protocol, strongly typed contracts, lower overhead) but for a gateway-facing API, REST's simplicity and debuggability wins.

---

**Q: How do you validate incoming requests?**

Spring's Bean Validation (`@Valid` with JSR-380 annotations). Request DTOs are annotated with constraints like `@NotNull`, `@Positive` (amount must be greater than zero), `@NotBlank` (account IDs can't be empty), and `@Size`. When a request fails validation, Spring automatically returns a 400 with field-level error details before the request even reaches the service layer. This keeps validation out of business logic — the service only receives requests that are structurally correct.

---

**Q: What HTTP status codes does your API use and why?**

- `200 OK` — successful GET or query
- `201 Created` — successful resource creation (new account, new transaction)
- `400 Bad Request` — invalid input, insufficient funds, business rule violation
- `401 Unauthorized` — missing or invalid JWT
- `404 Not Found` — account or transaction doesn't exist
- `409 Conflict` — duplicate idempotency key (transaction already exists)
- `422 Unprocessable Entity` — request is well-formed but fails validation rules
- `429 Too Many Requests` — rate limit exceeded
- `503 Service Unavailable` — circuit breaker open, downstream service unreachable

Using the right status codes matters because clients — and monitoring systems — use them to decide what to do next. A 409 tells the client "your request was already processed"; a 503 tells it "retry later".

---

**Q: What is a DTO and why do you use them instead of returning JPA entities directly?**

A DTO (Data Transfer Object) is a plain class that represents only the data you want to send over the wire. If I return a JPA entity directly, I expose internal details (database IDs, version fields, lazy-loaded collections that trigger extra queries, internal audit fields). DTOs let me control exactly what the API contract looks like, independent of the database schema. It also prevents accidentally serializing sensitive fields and decouples the API contract from database migrations — I can change the entity without changing the API response.

---

## 13. Testing

**Q: How did you test this project?**

The project has two focused tests. A cache load test that verifies the balance caching and eviction behavior under concurrent reads — it runs multiple threads all reading the same account balance to confirm Redis serves the cached value and doesn't hammer the database on every call. A retry behavior test for the gateway that verifies the Resilience4j circuit breaker and retry configuration — it simulates downstream failures and asserts the circuit breaker transitions correctly between CLOSED, OPEN, and HALF_OPEN states. For a production system I would add full unit test coverage for the service layer and integration tests using Testcontainers.

---

**Q: What is Testcontainers and why would you use it?**

Testcontainers is a library that spins up real Docker containers (PostgreSQL, Redis, Kafka) during your test run and tears them down afterwards. Instead of mocking the database or using an in-memory H2 database that behaves differently from PostgreSQL, your tests run against the real thing. This catches issues that mocks hide — actual SQL behavior, Liquibase migration correctness, Redis TTL behavior, Kafka partition assignment. The tradeoff is slower test startup, but the confidence you get is far higher than mocks.

---

**Q: What is the difference between unit tests and integration tests?**

A unit test tests a single class in isolation — all dependencies are replaced with mocks. It's fast (milliseconds) and precise — when it fails, you know exactly which class broke. An integration test tests multiple components together — typically the service + real database, or the full HTTP stack. It's slower but catches wiring mistakes that unit tests miss (wrong SQL query, wrong transaction boundary, incorrect Spring configuration). For a financial system, integration tests are especially important because the interesting failures are in the interactions — an `@Transactional` rollback that doesn't cover what you thought it did, or a cache eviction that happens in the wrong order.

---

**Q: If you had to add tests now, where would you start?**

The transaction service first — specifically `postTransfer`. This is the most critical path: balance check, journal entry creation, idempotency enforcement, Kafka publish. I'd write unit tests with mocked repositories covering: successful transfer, insufficient funds, duplicate idempotency key, and one account not found. Then I'd write an integration test with Testcontainers that runs the full flow against a real PostgreSQL instance to verify the DB transaction actually commits correctly and the balance computes accurately after the transfer.

---

## 14. Personal & Reflective Questions

**Q: How long did this project take you to build?**

I built it incrementally — starting with the core ledger service and working outwards. The domain model and transaction logic came first, then auth, then the gateway with circuit breakers and rate limiting, then adding Redis caching and Kafka for notifications, and finally adding structured logging with correlation IDs. Each layer built on the previous one, which helped me understand how the pieces interact rather than building everything at once.

---

**Q: What did you learn from building this project?**

A few things stood out. First, distributed systems have a completely different failure model than monoliths — things like partial failures, eventual consistency, and the impossibility of atomic writes across service boundaries aren't obvious until you're debugging them. Second, caching in a financial system is harder than it looks — the eviction logic has to be exactly right, or you're serving wrong data. Third, idempotency isn't just a nice-to-have for payment APIs — it's fundamental, because the network is unreliable and clients will retry. And finally, observability should be built in from day one, not added later — debugging without correlation IDs across five services is extremely painful.

---

**Q: What was the most challenging part of this project?**

Getting the transaction flow correct end-to-end — the interaction between `@Transactional`, `@CacheEvict`, and the Kafka publish. The cache eviction has to happen after the transaction commits, not before, otherwise a read between the eviction and the commit would repopulate the cache with a pre-commit balance. And the Kafka event should only be published if the transaction actually committed — not inside the transaction boundary where a rollback would have already fired the event. Understanding the exact order of operations and how Spring's proxies manage these boundaries was the most subtle part of the project.

---

**Q: How does this project relate to real-world systems you'd work on?**

The patterns are the same ones used in production fintech systems — double-entry accounting, idempotent APIs, event-driven architecture, circuit breakers at the gateway, distributed caching. The scale is obviously different — a production payment processor handles millions of transactions per day with strict regulatory requirements — but the core engineering problems are the same: atomicity, consistency, reliability under failure, and auditability. Building this gave me a concrete mental model of why those patterns exist and what goes wrong when you skip them.

---

**Q: What would you build next to improve this project?**

In order of impact: proper test coverage with Testcontainers, the Outbox Pattern to make Kafka publishing reliable, a database-level lock on balance reads to prevent double-spend, proper secrets management, and then distributed tracing with Zipkin. After that I'd add a proper API for account statements (paginated transaction history), currency support with exchange rates, and transaction reversal. On the infrastructure side — Kubernetes manifests, Prometheus metrics, and Grafana dashboards would make it genuinely production-grade.

---

**Q: Have you ever used this kind of system in a professional context?**

This project was built to understand the engineering decisions behind systems I've seen or worked near — payment rails, core banking backends, and internal transfer systems all solve the same fundamental problems: how do you move money reliably, verify it moved correctly, and handle every possible failure without losing money or double-charging? Building it from scratch gave me a much deeper understanding of why things like the Outbox Pattern, idempotency keys, and reconciliation exist — they're not theoretical patterns, they're solutions to specific, painful production failures.

---

## 15. Follow-Up Questions — Architecture & Design

> These are the "okay but..." and "what if..." questions interviewers ask right after your first answer.

**Q: You said microservices communicate over HTTP — what exactly happens when ledger-service needs to call auth-service?**

The gateway calls auth-service to validate the JWT using Spring WebClient (the reactive HTTP client). If auth-service is healthy, it returns a 200 with a boolean. The gateway then attaches `X-User-Id` and `X-User-Role` as headers and forwards the original request to ledger-service. Ledger-service reads those headers directly — it never calls auth-service itself. So the inter-service call is gateway → auth-service only; ledger-service is downstream and trusts the headers the gateway already validated.

---

**Q: You mentioned Eureka — what happens if Eureka itself goes down?**

Eureka clients cache the registry locally. If Eureka goes down, services continue to use the last known list of instances they fetched. New instances that start while Eureka is down won't be discoverable, and instances that die won't be removed from the cache — so some requests might hit dead instances until the health check timeout clears them. Eureka is designed to be highly available itself (you run it in a cluster), but even a single-node Eureka failure is survivable for a short period due to client-side caching.

---

**Q: You use Spring Cloud Gateway — what's the difference between a gateway and a reverse proxy like Nginx?**

A reverse proxy (Nginx, HAProxy) is infrastructure-level — it routes HTTP requests based on URL patterns, handles SSL termination, and does load balancing. It has no knowledge of your application code. A Spring Cloud Gateway is application-level — it runs as a Spring Boot app and can execute arbitrary Java code in its filter chain: call auth-service to validate a JWT, read Redis to enforce rate limits, make routing decisions based on request headers. The tradeoff is that Nginx is much faster and lighter; Gateway is more flexible but is another JVM process to manage.

---

**Q: How does load balancing work between multiple instances of ledger-service?**

Spring Cloud Gateway uses client-side load balancing via Spring Cloud LoadBalancer. When a route uses `lb://ledger-service`, the load balancer queries Eureka for all healthy instances of that service and picks one using a round-robin strategy by default. There's no external load balancer involved — the decision happens inside the gateway process. This is simpler to set up than a dedicated load balancer but means each gateway instance makes its own independent balancing decisions.

---

**Q: What is ACID? Why do you keep mentioning it?**

ACID stands for Atomicity (all operations in a transaction succeed or all fail), Consistency (the database moves from one valid state to another), Isolation (concurrent transactions don't see each other's intermediate state), and Durability (committed data survives crashes). For a financial ledger, every single property matters. Atomicity ensures both journal entries post or neither does. Isolation ensures a balance read doesn't see a half-committed transfer. Durability ensures a settled transaction is never lost after a crash.

---

**Q: What is eventual consistency and where does it appear in your system?**

Eventual consistency means different parts of the system may temporarily disagree on the state of data, but they will converge to the same state given enough time. In my system it appears in two places: the Redis cache (a balance read can serve a value up to 5 minutes stale if eviction didn't happen) and the notification flow (the notification arrives after the transaction commits, not in the same instant). The core ledger (PostgreSQL) is strongly consistent — there's no eventual consistency in the financial records themselves.

---

## 16. Follow-Up Questions — Authentication & JWT

**Q: What exactly is inside a JWT? Can you break it down?**

A JWT has three parts separated by dots. The header is a Base64-encoded JSON with the algorithm (`HS256`) and token type. The payload is Base64-encoded JSON with claims — in my case `sub` (user ID), `role`, and `exp` (expiry timestamp). The signature is `HMAC-SHA256(header + "." + payload, secret)`. The server verifies the signature using the secret key — if the payload was tampered with, the signature won't match and the token is rejected. The payload is not encrypted — anyone can decode it — so never put sensitive data (passwords, card numbers) in a JWT.

---

**Q: Where should the client store the JWT? Is localStorage safe?**

`localStorage` is convenient but vulnerable to XSS (Cross-Site Scripting) — if an attacker injects JavaScript into your page, they can read the token from `localStorage`. The safer option is an `HttpOnly` cookie, which JavaScript cannot access at all, so XSS can't steal it. The tradeoff is that cookies are vulnerable to CSRF (Cross-Site Request Forgery), which you mitigate with a CSRF token or `SameSite=Strict`. For a pure API backend (no browser frontend), this is less of a concern — mobile and server-to-server clients don't have these browser-specific risks.

---

**Q: What if someone steals the access token? Can you invalidate it?**

A stateless JWT cannot be revoked before it expires — that's the fundamental tradeoff you make by choosing JWTs over sessions. The mitigation is keeping the access token short-lived (15 minutes). If you need immediate revocation, you'd add a token blocklist in Redis — on logout or suspected compromise, store the token's JTI (unique ID) in Redis with TTL equal to the token's remaining lifetime. Every validation checks the blocklist. This adds a Redis round-trip to every request but gives you revocation capability.

---

**Q: What is the difference between authentication and authorization? Does your system do both?**

Authentication answers "who are you?" — verifying identity by validating credentials and issuing a JWT. Authorization answers "what are you allowed to do?" — enforcing permissions on specific resources. My system does authentication properly. Authorization is partial — the gateway passes the user's role in a header and ledger-service reads it, but there's no fine-grained ownership check — any authenticated user can technically query any account by ID. A complete implementation would check that the authenticated user owns the account they're accessing.

---

**Q: What is BCrypt and why do you use it for passwords?**

BCrypt is a password hashing function designed to be slow. When a user registers, their password is run through BCrypt which produces a hash stored in the database — the plaintext password is never stored. BCrypt includes a salt (random bytes mixed into the hash) automatically, so two users with the same password get different hashes. The "slowness" is intentional — it makes brute-force attacks impractical. MD5 and SHA-256 are too fast; an attacker with a GPU can try billions of hashes per second. BCrypt's configurable work factor lets you increase the cost as hardware gets faster.

---

## 17. Follow-Up Questions — Transactions & Data

**Q: Can you give me a concrete example of a double-entry transaction?**

Say User A transfers £100 to User B. The system creates two journal entries in the same DB transaction: DEBIT £100 on User A's account (money leaves) and CREDIT £100 on User B's account (money arrives). User A's balance = sum of all CREDITs minus sum of all DEBITs = drops by £100. User B's balance = increases by £100. The total money in the system is unchanged — it just moved. If you sum all debits and all credits across all accounts, they cancel out to zero. That's the fundamental invariant.

---

**Q: What are the possible states a transaction can be in?**

Three states: `PENDING` (created but not yet fully processed — used briefly during creation), `SETTLED` (both journal entries committed successfully — the normal end state), and `REVERSED` (a previously settled transaction that was undone — creates a compensating pair of journal entries to undo the original ones). The reconciliation report only counts SETTLED and REVERSED transactions, not PENDING ones, because pending transactions haven't fully committed yet.

---

**Q: What if someone sends a negative amount? What if they send zero?**

Both are rejected at the validation layer before the request reaches the service. The `TransferRequest` DTO has `@Positive` on the amount field — Bean Validation rejects zero and negative values at the controller boundary and returns a 422 Unprocessable Entity. This is enforced structurally so the business logic never sees invalid amounts. It's important to do this because negative amounts would reverse the debit/credit direction and could be used to steal money.

---

**Q: What happens to the idempotency key if the transfer fails? Can the client reuse it?**

The idempotency key is stored regardless of whether the transaction succeeded or failed. If the transfer failed with an `InsufficientFundsException`, that failure is associated with the idempotency key. If the client retries with the same key, they get the same failure response back — not a new attempt. This is intentional: idempotency guarantees the same outcome for the same key, success or failure. If the client wants to retry after fixing the underlying issue (e.g. the user added funds), they must generate a new idempotency key.

---

**Q: What is an N+1 query problem and does your project have one?**

An N+1 problem happens when you load N records and then issue one additional query per record — 1 + N total queries instead of 1. Yes, the reconciliation `findDiscrepancies()` has this: it loads all accounts (1 query) and then calls `getAccountBalance(id)` for each account (N queries). For 10,000 accounts that's 10,001 database round trips. The fix is a single aggregate query that computes the balance for all accounts at once, or batching. It's fine for a small dataset but would be very slow at scale.

---

**Q: What is the difference between optimistic and pessimistic locking?**

Pessimistic locking acquires a database lock on a row before reading it (`SELECT FOR UPDATE`) — no other transaction can modify that row until you release the lock. It prevents conflicts by making concurrent writers queue up. Optimistic locking doesn't lock anything upfront — it reads the data freely but includes a version number in the UPDATE: `WHERE id = ? AND version = ?`. If another transaction updated the row in between, the version won't match, and you get a conflict exception to handle. Pessimistic is safer but slower (causes contention); optimistic is faster but requires retry logic on conflict.

---

## 18. Follow-Up Questions — Caching & Redis

**Q: What is a cache stampede and how would you prevent it?**

A cache stampede happens when a heavily-used cache entry expires and hundreds of simultaneous requests all find it missing and all hit the database at once to recompute it — overloading the DB. Prevention strategies: probabilistic early expiration (randomly refresh the cache slightly before it expires, before the stampede happens), a distributed lock (only one process recomputes while others wait), or never fully expiring critical entries (background refresh instead of TTL expiry). For account balances, the risk is real for high-volume accounts.

---

**Q: What is the difference between Redis and a regular database?**

Redis stores all data in memory, making reads and writes microseconds fast. A relational database stores data on disk (with a cache in RAM), making reads milliseconds fast. Redis is not a replacement for a database — it doesn't have ACID transactions across multiple keys, no relational model, and data can be lost if not configured for persistence. Redis is best for data that's fast-changing, can be recomputed if lost (like a cache), or needs sub-millisecond access (like rate-limiting counters). I wouldn't store the journal entries in Redis — only the derived balance cache.

---

**Q: What happens if Redis goes down? Does your whole system break?**

No — Redis is a cache, not the source of truth. If Redis is unavailable, `@Cacheable` falls back to the database. Balance reads are slower (DB aggregate query instead of Redis lookup), and rate limiting stops working (which means unlimited requests temporarily), but the core transaction functionality continues. This is graceful degradation. The system would be slower and unprotected from abuse, but it wouldn't lose data or stop working entirely.

---

**Q: What is TTL and why did you set it to 5 minutes specifically?**

TTL (Time To Live) is how long a cached entry lives before Redis automatically deletes it. Five minutes is a pragmatic choice for account balances — it's short enough that stale data is limited to a small window, and long enough to absorb the read load from frequent balance checks. There's no scientifically correct TTL; it's a trade-off between staleness tolerance and cache hit rate. A 30-second TTL would give fresher data but lower hit rate and more DB load. For a real system I'd tune it based on measured read patterns and how frequently accounts actually change.

---

## 19. Follow-Up Questions — Kafka & Messaging

**Q: What is the difference between a Kafka topic and a partition?**

A topic is a named stream of events — like a category. A partition is how a topic is divided for parallel processing. A topic with 3 partitions can be read by up to 3 consumers simultaneously, each handling one partition. Messages within a partition are strictly ordered. Messages across partitions have no ordering guarantee. Partitioning is also how Kafka scales horizontally — more partitions = more parallelism. In my project, `transaction-settled` is the topic; I'd partition it by account ID so all events for the same account go to the same partition and are processed in order.

---

**Q: What if you have more consumers than partitions?**

Extra consumers sit idle. Kafka assigns at most one consumer per partition in a consumer group. If you have 3 partitions and 5 consumers, 3 are active and 2 wait. If an active consumer dies, Kafka reassigns its partition to one of the idle consumers. This means you can't scale beyond the number of partitions — if you want more parallelism, increase the partition count (though this requires care as it changes the routing of messages by key).

---

**Q: What is at-least-once delivery and what does it mean for your notification service?**

At-least-once means Kafka guarantees every message will be delivered to the consumer, but might deliver it more than once if the consumer crashes after processing but before committing its offset. For the notification service, this means a settlement notification could be sent twice to the same user. The notification handler needs to be idempotent — sending a duplicate notification is a minor UX issue (annoying), not a correctness issue (no money moved twice). This is acceptable. If I were processing financial debits inside the consumer, I'd need exactly-once guarantees.

---

**Q: How would you replay messages from the Dead Letter Topic?**

You'd write a separate consumer (or a manual script) that reads from the DLT, optionally filters or transforms the messages, and re-publishes them to the original topic. Kafka's CLI (`kafka-console-consumer` + `kafka-console-producer`) can do this manually. Before replaying, you'd fix the bug that caused the failures in the first place, otherwise the messages will fail again. In a production setup you'd have a UI (like Kafdrop, which is already in this project's Docker Compose) to inspect DLT messages and trigger replays.

---

**Q: What is a Kafka offset and why does it matter?**

An offset is a sequential number that identifies each message within a partition — message 0, 1, 2, 3... Each consumer group tracks its own offset per partition, meaning it knows which messages it has already processed. When a consumer restarts, it resumes from its last committed offset — not from the beginning. This is what gives Kafka its "no messages lost on restart" guarantee. If you reset the offset to 0 (the beginning), the consumer reprocesses all historical messages — which is how event replay works.

---

## 20. Follow-Up Questions — Circuit Breakers & Resilience

**Q: What are the three states of a circuit breaker?**

CLOSED (normal operation — requests go through, failures are counted), OPEN (the circuit has tripped — all requests are immediately rejected with a fallback, no calls made to the downstream service), and HALF_OPEN (a test state — one probe request is allowed through after the wait period; if it succeeds the breaker resets to CLOSED, if it fails it returns to OPEN). In this project the OPEN wait time is 30 seconds and the failure threshold to trip is 50% over a 10-call sliding window.

---

**Q: What is exponential backoff and why use it over fixed retry delays?**

Fixed backoff retries at the same interval every time — 1s, 1s, 1s. Exponential backoff doubles the wait on each failure — 1s, 2s, 4s, 8s. The reason is to avoid the thundering herd problem: if a service has a brief overload and thousands of clients all retry on fixed 1s intervals, they all hit the service again simultaneously and potentially cause another overload. Exponential backoff spreads retries out over time, giving the service a chance to recover. Combined with jitter (a random factor added to the delay), it's even more effective.

---

**Q: What is a fallback and what does yours return?**

A fallback is the response returned when the circuit breaker is open or a request fails. In my gateway configuration, the fallback is a `forward:/service-unavailable` route — a simple endpoint that returns a JSON response like `{"error": "Service temporarily unavailable, please try again later"}` with a 503 status. The client knows not to retry immediately. In a more sophisticated system, the fallback might return cached data (stale but useful) or a degraded response rather than an error — for example, showing a cached account balance instead of an error page.

---

**Q: What is a bulkhead pattern? Did you implement it?**

Bulkhead limits how many concurrent requests can be sent to a specific downstream service, preventing one slow service from consuming all available threads and starving requests to other services. Think of ship bulkheads — a leak in one compartment doesn't flood the whole ship. I didn't explicitly implement it, but Resilience4j supports `@Bulkhead` annotations. In practice, configuring separate HTTP connection pools per downstream service achieves the same isolation — ledger-service getting slow can't exhaust the connection pool for auth-service.

---

## 21. Follow-Up Questions — Database & Migrations

**Q: What happens if a Liquibase migration fails halfway through?**

Liquibase wraps each changeset in a database transaction where possible (DDL statements like `CREATE TABLE` are transactional in PostgreSQL). If the migration fails, the transaction rolls back and the `DATABASECHANGELOG` table doesn't record the changeset as applied. On the next startup, Liquibase retries the failed changeset. For statements that can't be rolled back (some DDL in MySQL, large data migrations), a failed migration can leave the schema in a partial state — this is why careful migration design and testing migrations in staging before production is important.

---

**Q: What is the difference between Liquibase and Flyway?**

Both are database migration tools. Liquibase uses XML, YAML, or JSON changelog files and supports rollback scripts natively. Flyway uses plain SQL files named with a version convention (`V1__create_accounts.sql`) and is simpler to get started with. Liquibase's rollback support is more flexible — Flyway's rollback requires a paid edition for automatic rollback. Flyway is generally considered easier for teams that prefer writing raw SQL; Liquibase is more powerful for complex change management. I chose Liquibase for the rollback capability, which matters in a financial system where bad migrations need to be undone cleanly.

---

**Q: Why not use an in-memory database like H2 for testing?**

H2 is a different database engine — it has different SQL syntax, different behaviour for constraints, different locking semantics, and doesn't support all PostgreSQL-specific features (like `JSONB`, certain index types, or `ON CONFLICT`). A test that passes on H2 can fail on PostgreSQL in production because they behave differently. Testcontainers spins up a real PostgreSQL instance, so your tests run against the exact same engine as production. This is more important as your queries get more sophisticated.

---

## 22. Follow-Up Questions — Deployment & Infrastructure

**Q: What is the difference between a liveness probe and a readiness probe in Kubernetes?**

A liveness probe asks "is this container still alive?" — if it fails, Kubernetes restarts the pod. Use it to detect deadlocks or corrupted state from which the app can't recover on its own. A readiness probe asks "is this container ready to receive traffic?" — if it fails, Kubernetes removes the pod from the load balancer but doesn't restart it. Use it during startup (the app is running but still warming up) or when temporarily overwhelmed (stop sending traffic without killing the pod). Both matter: liveness restarts broken containers; readiness prevents healthy containers from being sent traffic they can't handle yet.

---

**Q: What is the difference between horizontal and vertical scaling?**

Vertical scaling (scaling up) means giving the existing machine more resources — more CPU, more RAM. It has a hard limit (the biggest available machine) and requires downtime for the upgrade. Horizontal scaling (scaling out) means adding more instances of the service. It has no theoretical upper limit and can be done without downtime. For stateless services like ledger-service and the gateway, horizontal scaling is straightforward — add instances, load balancer distributes traffic. For stateful systems like the database, horizontal scaling is much harder and usually means read replicas or sharding.

---

**Q: What is Docker and what does Docker Compose add on top of it?**

Docker packages an application and all its dependencies into a container — a lightweight, isolated runtime that runs the same way everywhere. A single `docker run` starts one container. Docker Compose is a tool for defining and running multi-container applications — you describe all services, their configurations, environment variables, network connections, and volume mounts in a single `docker-compose.yml`. One `docker compose up` starts everything in the right order. Without Compose, starting this project (5 services + PostgreSQL + Redis + Kafka + Zookeeper + Kafdrop) would require 9 separate `docker run` commands with the right flags.

---

**Q: What is a Docker network and why do your services communicate using service names?**

Docker Compose creates a private bridge network for all services defined in the same `docker-compose.yml`. Within that network, each service's container name acts as a DNS hostname. So `gateway-service` can reach PostgreSQL at `postgres:5432` and Redis at `redis:6379` without knowing their actual IP addresses. This works because Docker's embedded DNS resolver maps container names to their current IPs automatically. Outside Docker (from your host machine), those names don't resolve — you access services through the published ports only.

---

## 23. Follow-Up Questions — API Design & Error Handling

**Q: What is the difference between a 400 and a 422?**

Both indicate a client error with the request, but they signal different problems. `400 Bad Request` is a broad "this request is malformed" — the server can't even parse it, or a required field is missing, or the value type is wrong. `422 Unprocessable Entity` means the server understood the request (it's well-formed) but the data fails semantic validation — for example, a transfer amount is a valid number but is negative, or an expiry date is in the past. I use 422 for Bean Validation failures and 400 for business rule violations like insufficient funds.

---

**Q: What is the difference between a 401 and a 403?**

`401 Unauthorized` means the request lacks valid credentials — the JWT is missing, expired, or invalid. The name is misleading; it's really about authentication, not authorization. `403 Forbidden` means the credentials are valid (you're authenticated) but you don't have permission for this specific resource — like a regular user trying to access an admin endpoint. My system returns 401 for JWT failures. A complete authorization implementation would return 403 when a user tries to access another user's account.

---

**Q: What is REST and is your API truly RESTful?**

REST is an architectural style with constraints: client-server separation, statelessness (each request is self-contained), uniform interface (standard HTTP methods and status codes), and resource-based URLs. My API follows most REST conventions — resources are nouns (`/accounts`, `/transactions`), HTTP verbs are used semantically (GET to read, POST to create), and status codes are meaningful. It's not fully "pure" REST — it doesn't implement HATEOAS (hypermedia links in responses telling clients what they can do next), which most real-world APIs skip because the overhead outweighs the benefit.

---

**Q: What is pagination and why does your API need it?**

Pagination splits large result sets into pages. Without it, `GET /transactions/account/{id}` for an active account with 100,000 transactions would return all 100,000 rows in one response — slow to query, huge payload, and probably crashes the client. With pagination, you return 20 at a time with a `page` and `size` parameter. I haven't implemented it in this version, but any endpoint that returns a list of records needs pagination in production. Cursor-based pagination (using the last seen ID instead of an offset) is more efficient for large datasets than offset-based pagination.

---
