# FinLedger: Microservices Payment Ledger

A production-grade payment platform implementing double-entry bookkeeping, distributed tracing, and event-driven architecture. Built with Java 21, Spring Boot, PostgreSQL, Redis, and Kafka.

**Tech Stack:** Java 21 · Spring Boot 4.0 · Spring Cloud Gateway · PostgreSQL · Redis · Kafka · Docker Compose

---

## What It Does

FinLedger processes financial transactions atomically using double-entry accounting:
- Every transfer creates exactly 2 journal entries (DEBIT + CREDIT) that commit together or rollback together
- Guarantees: `Σ(debits) = Σ(credits)` always holds
- Immutable journal provides complete audit trail
- Daily reconciliation verifies books are balanced

---

## Quick Start

```bash
# Start all services (first run: 3-4 minutes)
docker-compose up --build

# Test the API
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"name":"Wallet","type":"ASSET","currency":"INR"}'

# Interactive API docs
open http://localhost:8080/swagger-ui.html

# Service dashboard
open http://localhost:8761  # Eureka registry
```

---

## Architecture

```
┌──────────────────────────────────────────────────┐
│              API CONSUMERS                       │
│   (Web Apps, Mobile, CLI Tools, Postman)        │
└────────────────────────┬─────────────────────────┘
                         │ HTTP/REST
                         ↓
      ┌───────────────────────────────────────┐
      │     API GATEWAY (Port 8080)           │
      ├───────────────────────────────────────┤
      │ • JWT Token Validation                │
      │ • Rate Limiting (Redis-backed)        │
      │ • Correlation ID Injection            │
      │ • Request Transformation              │
      │ • Service Discovery (Eureka)          │
      └───────────────────────────────────────┘
         ↙               ↓                ↘
    Auth Service    Ledger Service   Notification
    (Port 8081)     (Port 8082)      Service
        │               ├─────────────────────┐
        ↓               ↓                     ↓
    PostgreSQL    PostgreSQL         Kafka Consumer
    (auth_db)     (ledger_db)
                       ├─────────────────────┐
                       ↓                     ↓
                   Redis Cache          Kafka Broker
                  (Performance)         (Events)
                       ↓
                  Eureka Registry (8761)
```

### Services

| Service | Port | Purpose |
|---------|------|---------|
| API Gateway | 8080 | Routing, JWT validation, rate limiting |
| Auth Service | 8081 | User authentication, JWT tokens |
| Ledger Service | 8082 | Transactions, reconciliation, caching |
| Notification Service | 8083 | Async event processing |
| Eureka | 8761 | Service discovery |

---

## Key Features

### 1. Atomic Transactions
Every transfer is atomic—both journal entries commit together or both rollback. Prevents partial updates.

### 2. Idempotency Protection
Same request twice with same `idempotencyKey` returns the same result. No duplicate charges.

```bash
POST /api/transactions
Body: {..., "idempotencyKey": "req-123"}
# Retry with same key → returns original response
```

### 3. Event-Driven Settlement
Transaction settlement publishes events to Kafka → Notification Service processes asynchronously.
- Ledger doesn't wait for notifications
- Failed events retry 3× then go to Dead Letter Topic
- Complete decoupling of services

### 4. Reconciliation Engine
Daily verification that all debits equal all credits:
```bash
GET /api/reconciliation/report?from=2026-06-01&to=2026-06-30
# Returns: balanced=true, totalDebits=50000, totalCredits=50000
```

### 5. Performance Caching
Balance queries cached in Redis → <2ms response (vs ~40ms database):
```bash
GET /api/accounts/{id}/balance
# Cache hit: ~2ms | Cache miss: ~40ms
# Auto-invalidated on transfers
```

### 6. Distributed Tracing
Every request gets a correlation ID that flows through all logs:
```
Request abc-123 → Gateway → Auth Service → Ledger → Kafka
All logs include: [abc-123] for request tracing
```

---

## Design Highlights

**Why Microservices?**
- Independent scaling (Ledger Service: 10 replicas, Auth: 2)
- Fault isolation (Auth down ≠ Ledger down)
- Technology freedom per service

**Why Double-Entry?**
- Financial correctness guaranteed mathematically
- Audit trail for compliance
- Prevents accidental money creation/destruction

**Why Kafka?**
- Decouples notification from ledger processing
- Events survive service restarts
- Automatic retry + Dead Letter Topic

**Why Redis?**
- 20× faster than database for balance queries
- TTL-based auto-expiration
- Cache invalidated on every write

---

## Testing

```bash
# All tests
mvn test

# Service-specific
mvn test -pl ledger-service

# Load tests (cache behavior)
mvn test -Dtest=CacheLoadTest
```

**Performance Targets:**
- Balance reads (cached): <50ms P95 ✓
- Transaction posts: <200ms ✓
- Concurrent users: 1000+ ✓

---

## Next Steps

**Deployable as-is** with `docker-compose up`. Optional enhancements:
- Kubernetes orchestration
- Managed PostgreSQL (RDS)
- Kafka cluster (3+ brokers)
- Prometheus + Grafana monitoring
- Centralized logging (ELK, Datadog)

---

## Documentation

- **[README-DOCKER.md](README-DOCKER.md)** — Docker setup, deployment, troubleshooting

---

**Version:** 1.0.0 | **Status:** Production-Ready | **Last Updated:** 2026-06-21
