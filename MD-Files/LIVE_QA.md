# Live Interview Q&A

> Questions asked during mock interview practice — added in real time.

---

**Q: Explain what N-able does in simple English with an example, and why do you want to join them?**

---

**Simple English explanation with an example:**

Imagine a small restaurant chain with 50 locations. Each location has computers — for billing, orders, staff management. The restaurant doesn't have an IT team. So they hire an IT company (called an MSP — Managed Service Provider) to look after all their computers.

Now that IT company has a problem: they're managing hundreds of clients just like this restaurant. They can't physically visit every location every time a computer has a problem. They need software that lets them see all those computers remotely, get an alert when something breaks, fix it without going on-site, make sure all computers have the latest security updates, and back up important data automatically.

**That software is what N-able builds.**

N-able's platform sits on the IT company's screen and shows every single device they manage across all their clients — online/offline, health status, security alerts, pending updates — all in one place. When a computer in location 23 of the restaurant chain has a virus, the IT company sees it instantly on N-able, clicks a button, and fixes it remotely. The restaurant never even knew there was a problem.

So in short:
- **End customer** = small businesses (restaurants, law firms, dental clinics) that can't afford their own IT team
- **N-able's direct customer** = the IT company (MSP) that manages those small businesses
- **N-able's product** = the platform the IT company uses to do their job

---

**Why do you want to join N-able?**

> "N-able operates at a genuinely interesting scale — their platform monitors millions of devices across thousands of IT companies worldwide. That means the backend has to be reliable, fast, and handle enormous amounts of real-time data — device health events, security alerts, patch statuses — all flowing in simultaneously from devices across the globe. That's the kind of distributed systems challenge I find interesting and want to work on.
>
> The other thing that appeals to me is that the product has real, tangible impact. When N-able's monitoring catches a ransomware attack early, it's protecting a small dental clinic or a local business that has no IT team of their own. The engineering work directly translates to businesses staying safe and operational.
>
> Coming from building a financial microservices system — where I dealt with reliability, event-driven architecture, and fault tolerance — N-able feels like a natural next step where I can apply those same patterns at real production scale, with the added complexity of monitoring infrastructure rather than just processing transactions."

---

**Key talking points if they ask follow-up "what do you know about our product?"**
- N-central is their flagship platform — enterprise RMM used by large MSPs
- N-sight is for smaller MSPs
- Cove Data Protection handles backup and recovery
- Spun off from SolarWinds in 2021 — now independent, publicly traded
- Security is a big focus — EDR (Endpoint Detection and Response) is a growing part of the product

---

**Q: To prevent double-spending we check idempotency keys — but where do we store them? Redis? And how is this done in real production?**

**First — idempotency keys and double-spend are related but not the same thing:**

- **Double-spend** = the same money being debited twice because of a race condition between two concurrent requests. Prevented by a DB lock on the balance check.
- **Idempotency** = the same request being processed twice because the client retried (network timeout, client crash). Prevented by idempotency keys.

Both matter. This question is specifically about idempotency keys.

---

**What this project does:**

`TransactionService.java` line 56:
```java
transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
    .ifPresent(t -> { throw new DuplicateTransactionException(...) });
```

The idempotency key is stored as a column on the `transactions` table with a `UNIQUE` constraint in the database. The flow is:
1. SELECT — check if the key exists in the transactions table
2. If not found — process the transfer and INSERT the transaction (the key gets saved as part of the transaction row)
3. If found — throw `DuplicateTransactionException`

This is simple and works — but has a race condition problem explained below.

---

**The race condition in this approach:**

The SELECT (step 1) and INSERT (step 2) are two separate operations. Two concurrent requests with the same idempotency key can both do the SELECT, both see "not found", both try to INSERT. One INSERT succeeds. The other hits the UNIQUE constraint and throws a `DataIntegrityViolationException` from the database. In this project that exception is not caught specifically — it falls through to the generic handler and returns a 500 instead of a 409. The DB constraint is the actual safety net, but the application doesn't handle it gracefully.

---

**How production applications actually do it — 3 approaches:**

---

**Approach 1 — Dedicated `idempotency_keys` table in the database (most common in fintech)**

This is what Stripe uses. You have a separate table:

```sql
CREATE TABLE idempotency_keys (
    key         VARCHAR PRIMARY KEY,
    status      VARCHAR,        -- 'in_flight', 'completed'
    response    JSONB,          -- the full response payload stored here
    status_code INT,
    created_at  TIMESTAMP,
    expires_at  TIMESTAMP
);
```

The flow:
1. `INSERT INTO idempotency_keys (key, status) VALUES (?, 'in_flight')` — atomic INSERT
2. If INSERT succeeds → you own this key, process the request
3. After processing → `UPDATE idempotency_keys SET status='completed', response=?, status_code=? WHERE key=?`
4. If INSERT fails (duplicate key violation) → SELECT the existing row and return its stored `response` directly

Why this is better than this project's approach:
- **Atomic** — the INSERT either succeeds or fails, no SELECT-then-INSERT race condition
- **Stores the response** — a retry gets back the exact same response including the transaction ID, HTTP status, everything. The client can't tell it was a retry.
- **In-flight detection** — if the key exists with status `in_flight`, another request is currently processing it. You can return 409 or make the retry wait.
- **Decoupled from the transactions table** — the idempotency logic is separate from the domain data

This is actually why there's an unused `idempotency_keys` migration in this project (`004-create-idempotency-keys.yaml`) — the table was created but never wired up with any code.

---

**Approach 2 — Redis as a fast first-pass check**

Some high-throughput systems use Redis for the initial check because it's much faster than a DB query:

```
SET idempotency:{key} "in_flight" EX 86400 NX
```

- `NX` = only set if the key does NOT already exist (atomic in Redis)
- `EX 86400` = expires after 24 hours
- If the SET succeeds → you own this key, process the request, then update the value to the response
- If the SET fails (key already exists) → return the stored response

**Why Redis alone is NOT enough for financial systems:**
- Redis can lose data — if Redis restarts without persistence configured, all idempotency keys are gone and duplicate requests get processed again
- Not atomic with your DB transaction — you can write to Redis but then the DB transaction rolls back, leaving a stale Redis entry that blocks future legitimate retries
- Redis is eventually consistent in a cluster — two nodes might briefly disagree on whether a key exists

**So production systems use Redis as a fast cache on top of the DB table, not instead of it.** Redis handles the 99% of normal retries cheaply and quickly. The DB table is the source of truth and the fallback.

---

**Approach 3 — Unique constraint only (simplest, fine for lower scale)**

Exactly what this project does — rely on the DB UNIQUE constraint as the atomic guard. The SELECT before it is just an optimisation to return a clean error message instead of a constraint violation exception. The constraint itself is what actually prevents duplicates.

This works correctly at low scale. The only requirement is that the application catches `DataIntegrityViolationException` and returns 409 instead of 500 — which this project currently doesn't do.

---

**Summary — which approach for which situation:**

| Approach | When to use |
|---|---|
| UNIQUE constraint only | Small scale, simple systems, internal tools |
| Dedicated `idempotency_keys` table | Fintech, payments, anything where you need to store and replay the exact response |
| Redis + DB table | High-throughput APIs (thousands of requests/sec) where DB lookup on every request is too slow |

---

**What to say in the interview:**
> "This project stores the idempotency key as a UNIQUE constraint on the transactions table — it's simple and works, but has a gap where a concurrent duplicate returns 500 instead of 409 because the DB constraint violation isn't caught cleanly. In production, Stripe's approach is better: a dedicated idempotency_keys table where you do an atomic INSERT first. If it succeeds you process the request. If it fails you return the stored response from the previous attempt. Redis can be added as a fast cache in front for high-throughput APIs, but the DB table is the source of truth because Redis alone can lose data."

---

**Q: How does reconciliation actually work in real production applications? Do they check every transaction or every account every day?**

**Short answer: There are two completely different types of reconciliation running simultaneously. One happens instantly per transaction, the other runs as a scheduled batch — usually end-of-day.**

---

**Type 1 — Real-time constraint check (per transaction, instant)**

This is not what most people call "reconciliation" but it's the first line of defence. Every time a transaction is posted, the system immediately verifies the double-entry invariant — debits must equal credits — before committing. If they don't balance, the transaction is rejected on the spot. This is what this project does inside `postTransfer()` — both journal entries are created in the same DB transaction, and if anything is wrong it rolls back. This runs on every single transaction, in real time, with zero delay.

This is a **constraint**, not reconciliation. It prevents bad data from ever entering the system.

---

**Type 2 — Scheduled batch reconciliation (periodic, not per transaction)**

This is what finance teams actually call reconciliation. It does NOT run per transaction — that would be impossibly slow at scale. Instead it runs on a schedule:

- **End-of-day (EOD)** — most common in banks. After market close, a batch job runs and checks everything that happened that day.
- **Intraday** — high-volume payment processors (Stripe, PayPal) run it every hour or every few hours.
- **Real-time streaming** — the most advanced setups (large card networks) use Kafka Streams or Apache Flink to run reconciliation continuously as events flow through the system.

---

**What exactly does the batch reconciliation check?**

There are two levels:

**Internal reconciliation** — checking your own system against itself.
- Sum all debits for the day. Sum all credits. They must be equal.
- Check every account: does the balance computed from journal entries match what the account record says?
- This is what this project implements — a date-range report that sums debits and credits and checks they balance.

**External reconciliation** — checking your system against an outside source. This is the more important one in production.
- A fintech company compares their internal ledger against the Visa/Mastercard settlement report received each morning.
- A bank reconciles their ledger against the central bank's RTGS (Real Time Gross Settlement) system.
- A payments company like Stripe compares every payout in their DB against the bank statement from their banking partner.
- Any row in your system that has no matching row in the external report is a "break" — an exception that someone investigates.

This project has no external reconciliation — it only checks internally. That's fine for a learning project but a real payments company's most critical reconciliation is always the external one.

---

**What happens when a discrepancy is found?**

1. The batch job writes the exception to an `exceptions` or `breaks` table — every mismatch is recorded with full details (account, amount, expected vs actual, timestamp).
2. An alert fires to the operations team (email, PagerDuty, Slack).
3. Operations analysts investigate manually — they look at the audit trail, check if a transaction got stuck in a pending state, whether a network timeout caused a partial post.
4. The break is either auto-resolved (a retry clears it) or manually resolved (an ops person posts a correcting entry).
5. A daily reconciliation report is produced for the finance/risk team showing how many breaks there were and how they were resolved.

At large banks, there are entire teams of people whose only job is investigating reconciliation breaks every morning.

---

**How is it triggered? Scheduled job or event-driven?**

Both, depending on the system:

| Trigger | When used |
|---|---|
| Cron job (midnight, 6am) | Standard EOD batch reconciliation — most banks |
| After a settlement batch closes | Triggered when a payment network sends its settlement file |
| Every N minutes (streaming) | High-frequency processors like Stripe, Adyen |
| On-demand by ops team | When a specific account is suspected to have an issue |

In Java/Spring, EOD batch reconciliation is typically built with **Spring Batch** — it handles chunked processing, restartability, and job history out of the box. This project uses a simple service method called on demand, which is fine for a demo but not how you'd build it for production.

---

**What this project does vs production:**

| | This project | Production |
|---|---|---|
| Per-transaction check | Yes — double-entry constraint enforced on every post | Yes — same |
| Internal batch reconciliation | Yes — date range report, sums debits and credits | Yes — EOD batch or streaming |
| External reconciliation | No | Yes — most critical part, compares against bank/network statements |
| Exception tracking | No — just logs | Yes — dedicated exceptions table, alert system, ops workflow |
| Scheduler | No — called on demand via API | Yes — Spring Batch cron, or event-triggered after settlement |
| Scale | Loads all accounts into memory | Chunked processing (Spring Batch), pagination, parallel workers |

---

**Interview one-liner:**
> "Production reconciliation has two parts — a real-time constraint check on every transaction that rejects imbalances instantly, and a scheduled batch job (usually end-of-day) that compares your internal ledger against external settlement reports from banks or card networks. My project implements the internal batch part. The external reconciliation — which is actually the most important — isn't implemented here."

---

**Q: How do you prevent someone from calling ledger-service directly, bypassing the gateway? How is this done in real production? And does your ledger-service actually re-validate the token?**

**How it's done in real production — 3 layers:**

Production systems use defense in depth — multiple layers, each independent. If one fails, the next stops it.

**Layer 1 — Network isolation (primary defense)**
This is the real answer. In production, internal services simply have no public IP address — they are physically unreachable from outside.

- **In Kubernetes**: ledger-service is a `ClusterIP` Service — it only exists inside the cluster network. The gateway is a `LoadBalancer` Service with a public IP. Nobody outside the cluster can even route a packet to ledger-service's port.
- **In AWS**: ledger-service runs in a private subnet. The gateway/ALB is in the public subnet. Security Group rules on ledger-service only allow inbound traffic from the gateway's Security Group — not from the internet.
- **In Docker Compose (this project)**: ledger-service does NOT publish its port to the host. Only the gateway publishes port 8080. So from your machine you can't even reach ledger-service directly.

This is the strongest protection because it requires zero application code — it's enforced at the infrastructure level.

**Layer 2 — mTLS / Service mesh (intermediate defense)**
Even if someone somehow reaches ledger-service on the network (e.g. a compromised internal pod), mTLS ensures only trusted services can complete a TLS handshake with it. Each service has a certificate. ledger-service trusts only certs signed by the internal CA — the gateway has one, a random attacker doesn't. In Kubernetes this is handled automatically by a service mesh like **Istio** or **Linkerd** — you don't write code for it.

**Layer 3 — JWT re-validation in the service itself (last line of defense)**
Even if someone got through layers 1 and 2, they still need a valid JWT to do anything. The service validates the token itself. This is defense in depth — it shouldn't be the only protection, but it's there as a fallback.

---

**Does this project's ledger-service actually re-validate the token?**

**Yes, it does — but the implementation is flawed.**

Looking at `JwtAuthenticationFilter.java`:
- Lines 38–44: it reads the `Authorization: Bearer <token>` header, verifies the JWT signature using the secret key, and sets the user in Spring Security's context. That part is correct.

**The flaw**: if JWT validation throws an exception (expired token, wrong signature, tampered payload), the catch block on line 50 just does `System.out.println(...)` and the code falls through to line 57 — `filterChain.doFilter(request, response)` — which continues processing the request anyway. The filter doesn't reject it. Whether an unauthenticated request ultimately gets blocked depends entirely on the Spring Security configuration (`http.authorizeHttpRequests()`). The filter itself is not the enforcement point.

**What it should do instead:**
```
} catch (Exception e) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.getWriter().write("Invalid token");
    return; // stop the chain here
}
```

**Summary — what to say in the interview:**

> "In production you rely primarily on network isolation — services have no public IP and security group rules only allow traffic from the gateway. That's the real protection. JWT re-validation in the service is a second layer — defense in depth — so even an internal attacker needs a valid token. In this project, the ledger-service does re-validate the JWT, but the filter has a flaw: it doesn't hard-stop the request on validation failure, it relies on Spring Security's config to do that. In production I'd fix that and add network-level isolation so the service is unreachable without going through the gateway in the first place."

---

 You said it provides ACID, decimal types, and row-level locking — but don't MySQL and NoSQL databases provide those too?**

**Honest answer — yes, partially, and here's the real distinction:**

**PostgreSQL vs MySQL:**

MySQL with InnoDB (its default storage engine) also supports ACID, DECIMAL types, and row-level locking — so you're right, MySQL would work for this project too. The real differences are more subtle:

- **Strictness**: PostgreSQL is far stricter about data integrity. MySQL will silently truncate a string that's too long, do implicit type coercions, and accept invalid dates. PostgreSQL throws an error. For financial data you want strict — silent data corruption is worse than a loud error.
- **Decimal precision**: PostgreSQL's `NUMERIC` type handles arbitrary precision arithmetic correctly. MySQL's `DECIMAL` works but PostgreSQL's implementation is considered more precise and reliable for complex financial calculations.
- **Standards compliance**: PostgreSQL follows the SQL standard much more closely. MySQL has historically had quirks (e.g. `GROUP BY` allowing columns not in the select list without aggregating them) that can cause subtle bugs in aggregate queries — exactly the kind you write for reconciliation.
- **Query power**: PostgreSQL has better support for window functions, CTEs (Common Table Expressions), and complex aggregations — all of which you'd use heavily in financial reporting queries.
- **Industry preference**: In the fintech and banking world PostgreSQL is the dominant choice precisely because of its stricter guarantees and reliability track record.

So the honest answer is: MySQL would work, but PostgreSQL is the safer choice for financial data because it fails loudly rather than silently.

---

**PostgreSQL vs NoSQL — do NoSQL databases support transactions?**

This is a common misconception worth clearing up. **Modern NoSQL databases DO support transactions** — but with important caveats:

- **MongoDB** has supported multi-document ACID transactions since version 4.0 (2018)
- **DynamoDB** has TransactWriteItems for atomic multi-item operations
- **Cassandra** has lightweight transactions using Compare-And-Set operations
- **FaunaDB** was designed from the ground up with ACID transactions

So the "NoSQL = no transactions" statement is outdated. The real reasons to not use NoSQL for a financial ledger are different:

1. **The data model is the wrong fit.** A financial ledger is inherently relational — accounts link to journal entries which link to transactions, with strict foreign key constraints enforcing that every debit has a matching credit. Forcing this into a document store (MongoDB) or key-value store (DynamoDB) means you either denormalise everything (and lose integrity guarantees) or write application-level join logic that the database would normally enforce for you.

2. **Transactions were bolted on, not designed in.** PostgreSQL was built around ACID from day one — 30+ years of battle-tested transactional semantics. MongoDB added multi-document transactions in 2018 as a feature addition. In practice this means PostgreSQL's transactional behaviour is more predictable, better documented, and has fewer edge cases.

3. **NoSQL transactions have practical limits.** MongoDB transactions have significant performance overhead compared to single-document operations. DynamoDB transactions are limited to 25 items per call. These limits don't exist in PostgreSQL — you can have thousands of rows in a single transaction with no special handling.

4. **SQL is the right language for reconciliation.** Summing debits, summing credits, filtering by date range, grouping by account — these are naturally expressed in SQL with `SUM`, `GROUP BY`, `WHERE`. In a NoSQL database you'd either use a limited aggregation pipeline (MongoDB) or pull data into application memory and aggregate there — slower, more error-prone, and harder to audit.

**The one-line summary:**
NoSQL databases can technically do transactions now, but PostgreSQL's relational model, strict data handling, and 30-year track record of ACID compliance make it the right tool for financial data — not because NoSQL literally can't do it, but because PostgreSQL was designed for exactly this problem.

---

