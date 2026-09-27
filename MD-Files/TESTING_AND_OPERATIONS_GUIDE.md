# FinLedger: Testing & Operations Guide
## Features 1-5 (Auth, Accounts, Transactions, State Machine, Idempotency)

---

## 📊 Quick Service Checklist

| Service | Port | Health URL | Purpose |
|---------|------|-----------|---------|
| Gateway | 8080 | `http://localhost:8080/actuator/health` | API entry point, routing, rate limiting |
| Auth Service | 8081 | `http://localhost:8081/actuator/health` | JWT token generation & validation |
| Ledger Service | 8082 | `http://localhost:8082/actuator/health` | Double-entry bookkeeping, transactions |
| Notification Service | 8083 | `http://localhost:8083/actuator/health` | Kafka event consumer |
| Eureka (Service Discovery) | 8761 | `http://localhost:8761` | Service registry dashboard |
| PostgreSQL | 5432 | (via psql) | auth_db, ledger_db |
| Redis | 6379 | (via redis-cli) | Cache layer |
| Kafka | 9092 | (via kafka CLI) | Event streaming |
| **Swagger UI (Auth)** | 8081 | `http://localhost:8081/swagger-ui/index.html` | API docs & testing |
| **Swagger UI (Ledger)** | 8082 | `http://localhost:8082/swagger-ui/index.html` | API docs & testing |

---

## 🔍 FEATURE 1: AUTH SERVICE (Spring Security + JWT)

### What You're Testing
- User registration with secure password hashing (BCrypt)
- User login with password validation
- JWT token generation and validation
- Token refresh mechanism
- Service-to-service authentication

### Test Plan

#### 1.1 Register a New User
```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "SecurePass123!"
  }'
```

**Expected Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400,
  "tokenType": "Bearer"
}
```

**What This Tests:**
- ✅ BCrypt password hashing (never store plaintext)
- ✅ JWT token generation with HS256 signature
- ✅ Token expiry (24 hours for access token, 7 days for refresh)
- ✅ Database persistence (user created in auth_db)

#### 1.2 Login with Credentials
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "password": "SecurePass123!"
  }'
```

**Expected Response:** Same JWT tokens as registration

**What This Tests:**
- ✅ Password validation against BCrypt hash
- ✅ Token generation on successful login
- ✅ Rejection on wrong password (401 Unauthorized)

#### 1.3 Decode & Inspect the JWT Token
```bash
# Copy the accessToken value and decode it at https://jwt.io
# OR decode in bash:
TOKEN="<paste-your-access-token-here>"
echo $TOKEN | cut -d. -f2 | base64 -d | jq .
```

**Expected Payload:**
```json
{
  "sub": "user-id-uuid-here",
  "username": "alice",
  "roles": ["USER"],
  "iat": 1782021737,
  "exp": 1782108137
}
```

**What This Tests:**
- ✅ Token carries user identity (sub claim)
- ✅ Token carries roles for authorization
- ✅ Token is time-bound (iat = issued at, exp = expiration)

#### 1.4 Refresh Token
```bash
curl -X POST http://localhost:8081/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<your-refresh-token-here>"
  }'
```

**Expected Response:** New access token with extended expiry

**What This Tests:**
- ✅ Refresh token is valid and in database
- ✅ Refresh token hasn't expired
- ✅ New access token is properly signed

#### 1.5 Use Token to Access Protected Endpoint
```bash
# This will fail without token (401)
curl http://localhost:8082/api/accounts

# This will succeed with token (200)
curl http://localhost:8082/api/accounts \
  -H "Authorization: Bearer <your-access-token>"
```

**What This Tests:**
- ✅ JWT validation on protected endpoints
- ✅ Token signature verification
- ✅ Token expiry checking

#### 1.6 Security Tests
```bash
# Try to register duplicate username (should fail)
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice2@example.com", "password": "Pass123!"}'

# Try to login with wrong password (should fail)
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "WrongPassword"}'

# Try to modify JWT payload and retry (should fail)
# Edit the JWT at jwt.io, then try to use the modified token
```

---

## 💳 FEATURE 2: LEDGER SERVICE (Accounts & Core Logic)

### What You're Testing
- Account creation with proper types (Asset, Liability)
- Account balance computation from journal entries
- Account activation/deactivation
- Currency validation

### Test Plan

#### 2.1 Create First Account
```bash
curl -X POST http://localhost:8082/api/accounts \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Wallet",
    "type": "ASSET",
    "currency": "INR",
    "initialBalance": 10000
  }'
```

**Expected Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Alice Wallet",
  "type": "ASSET",
  "currency": "INR",
  "isActive": true,
  "createdAt": "2026-06-21T06:00:00Z"
}
```

**What This Tests:**
- ✅ Account created with UUID
- ✅ Currency stored correctly
- ✅ Account marked as active by default
- ✅ Database persistence

#### 2.2 Create Second Account
```bash
curl -X POST http://localhost:8082/api/accounts \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bob Wallet",
    "type": "ASSET",
    "currency": "INR"
  }'
```

**Expected Response:** Similar to 2.1

#### 2.3 Get Account Details
```bash
curl http://localhost:8082/api/accounts/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <your-token>"
```

**Expected Response:** Full account object

#### 2.4 Query Account Balance
```bash
curl http://localhost:8082/api/accounts/550e8400-e29b-41d4-a716-446655440000/balance \
  -H "Authorization: Bearer <your-token>"
```

**Expected Response:**
```json
{
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": 10000,
  "currency": "INR",
  "asOf": "2026-06-21T06:05:00Z"
}
```

**What This Tests:**
- ✅ Balance computed from journal entries
- ✅ Currency included in response
- ✅ Timestamp shows when balance was computed

#### 2.5 Security Tests
```bash
# Try to create account without token (401)
curl -X POST http://localhost:8082/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"name": "Test", "type": "ASSET", "currency": "INR"}'

# Try to create with invalid currency (should fail validation)
curl -X POST http://localhost:8082/api/accounts \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test", "type": "ASSET", "currency": "INVALID"}'

# Try to create account with negative balance (should fail)
curl -X POST http://localhost:8082/api/accounts \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test", "type": "ASSET", "currency": "INR", "initialBalance": -1000}'
```

---

## 💸 FEATURE 3: DOUBLE-ENTRY TRANSACTIONS

### What You're Testing
- Atomic transfer between accounts
- Journal entry creation (debit & credit)
- Balance consistency (double-entry rule: debits = credits)
- Transaction status lifecycle (PENDING → SETTLED)

### Test Plan

#### 3.1 Post a Transfer
```bash
curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "destinationAccountId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": 1000,
    "currency": "INR",
    "description": "Payment for services",
    "idempotencyKey": "txn-alice-bob-001"
  }'
```

**Expected Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "amount": 1000,
  "currency": "INR",
  "status": "SETTLED",
  "sourceAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "destinationAccountId": "550e8400-e29b-41d4-a716-446655440001",
  "createdAt": "2026-06-21T06:10:00Z",
  "settledAt": "2026-06-21T06:10:00Z"
}
```

#### 3.2 Verify Balances Updated
```bash
# Alice should have 10000 - 1000 = 9000
curl http://localhost:8082/api/accounts/550e8400-e29b-41d4-a716-446655440000/balance \
  -H "Authorization: Bearer <your-token>" | jq .balance

# Bob should have 0 + 1000 = 1000
curl http://localhost:8082/api/accounts/550e8400-e29b-41d4-a716-446655440001/balance \
  -H "Authorization: Bearer <your-token>" | jq .balance
```

**What This Tests:**
- ✅ DEBIT applied to source (decreased by 1000)
- ✅ CREDIT applied to destination (increased by 1000)
- ✅ Double-entry rule maintains balance (net = 0)

#### 3.3 Check Journal Entries
```bash
curl http://localhost:8082/api/accounts/550e8400-e29b-41d4-a716-446655440000/entries \
  -H "Authorization: Bearer <your-token>"
```

**Expected Response:**
```json
[
  {
    "id": "...",
    "transactionId": "550e8400-e29b-41d4-a716-446655440002",
    "accountId": "550e8400-e29b-41d4-a716-446655440000",
    "entryType": "DEBIT",
    "amount": 1000,
    "createdAt": "2026-06-21T06:10:00Z"
  }
]
```

**What This Tests:**
- ✅ Journal entries immutable (write-once)
- ✅ Entry type correctly recorded (DEBIT/CREDIT)

#### 3.4 Error Cases
```bash
# Try to transfer more than available balance
curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "550e8400-e29b-41d4-a716-446655440001",
    "destinationAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "amount": 5000,
    "currency": "INR",
    "description": "Will fail",
    "idempotencyKey": "txn-bob-alice-002"
  }'
# Should return 400 Bad Request - Insufficient Balance

# Try to transfer zero amount
curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "destinationAccountId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": 0,
    "currency": "INR",
    "idempotencyKey": "txn-alice-bob-003"
  }'
# Should return 400 Bad Request - Amount must be > 0

# Try mismatched currency
curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "destinationAccountId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": 500,
    "currency": "USD",
    "idempotencyKey": "txn-alice-bob-004"
  }'
# Should return 400 Bad Request - Currency Mismatch
```

---

## 🔄 FEATURE 4: STATE MACHINE & TRANSACTION REVERSALS

### What You're Testing
- Transaction state transitions (PENDING → PROCESSING → SETTLED/FAILED → REVERSED)
- Invalid state transition rejection
- Reversal via counter-entries (original entries untouched)
- State immutability

### Test Plan

#### 4.1 Post a Transaction (Sets PENDING → SETTLED)
```bash
# Already tested above; status should be SETTLED
```

#### 4.2 Reverse the Transaction
```bash
curl -X POST http://localhost:8082/api/transactions/550e8400-e29b-41d4-a716-446655440002/reverse \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json"
```

**Expected Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440005",
  "status": "SETTLED",
  "reversalOfId": "550e8400-e29b-41d4-a716-446655440002",
  "amount": 1000,
  "description": "Reversal of transaction 550e8400-e29b-41d4-a716-446655440002",
  "createdAt": "2026-06-21T06:15:00Z"
}
```

#### 4.3 Verify Balances Restored
```bash
# Alice should be back to 10000
curl http://localhost:8082/api/accounts/550e8400-e29b-41d4-a716-446655440000/balance \
  -H "Authorization: Bearer <your-token>" | jq .balance

# Bob should be back to 0
curl http://localhost:8082/api/accounts/550e8400-e29b-41d4-a716-446655440001/balance \
  -H "Authorization: Bearer <your-token>" | jq .balance
```

**What This Tests:**
- ✅ Reversal creates counter-entries (new entries, not modifications)
- ✅ Original entries still exist and unchanged
- ✅ Balances restored to pre-transfer state

#### 4.4 Check Original Transaction Marked as REVERSED
```bash
curl http://localhost:8082/api/transactions/550e8400-e29b-41d4-a716-446655440002 \
  -H "Authorization: Bearer <your-token>"
```

**Expected Response:** status = "REVERSED"

#### 4.5 Invalid State Transition Tests
```bash
# Try to reverse a pending transaction (should fail)
# First, create a transaction that stays PENDING (we'll need to modify this)
# For now, try to reverse already-reversed transaction (should fail)
curl -X POST http://localhost:8082/api/transactions/550e8400-e29b-41d4-a716-446655440005/reverse \
  -H "Authorization: Bearer <your-token>"
# Should return 400 Bad Request - Invalid State Transition (REVERSED → cannot go anywhere)

# Try to reverse a FAILED transaction (if you can create one)
# Should return 400 Bad Request - Only SETTLED transactions can be reversed
```

---

## 🛡️ FEATURE 5: IDEMPOTENCY PROTECTION

### What You're Testing
- Race condition safety (same request twice = single transaction)
- Idempotency-Key header handling
- Duplicate request rejection

### Test Plan

#### 5.1 First Request with Idempotency-Key
```bash
curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-12345" \
  -d '{
    "sourceAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "destinationAccountId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": 500,
    "currency": "INR",
    "idempotencyKey": "unique-key-12345"
  }'
```

**Expected Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440010",
  "status": "SETTLED",
  "amount": 500,
  ...
}
```

**Store the transaction ID:** `550e8400-e29b-41d4-a716-446655440010`

#### 5.2 Duplicate Request (Same Idempotency-Key)
```bash
# Send the EXACT SAME request again
curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-12345" \
  -d '{
    "sourceAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "destinationAccountId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": 500,
    "currency": "INR",
    "idempotencyKey": "unique-key-12345"
  }'
```

**Expected Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440010",  // SAME ID as first request
  "status": "SETTLED",
  "amount": 500,
  ...
}
```

**What This Tests:**
- ✅ Same transaction ID returned (not a new transaction)
- ✅ Only ONE transaction in database (not two)
- ✅ Idempotency key prevents race conditions

#### 5.3 Verify Only One Transaction Exists
```bash
# Query transactions; should see only ONE with this idempotency key
curl http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <your-token>" | jq '.[] | select(.idempotencyKey == "unique-key-12345")'
```

**Expected Output:** Single transaction object

#### 5.4 Concurrent Request Test (Simulate Race Condition)
```bash
# Fire 10 identical requests simultaneously
for i in {1..10}; do
  curl -X POST http://localhost:8082/api/transactions \
    -H "Authorization: Bearer <your-token>" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: race-condition-key" \
    -d '{
      "sourceAccountId": "550e8400-e29b-41d4-a716-446655440000",
      "destinationAccountId": "550e8400-e29b-41d4-a716-446655440001",
      "amount": 100,
      "currency": "INR",
      "idempotencyKey": "race-condition-key"
    }' &
done
wait

# Check database: should have exactly ONE transaction with idempotencyKey="race-condition-key"
```

**What This Tests:**
- ✅ PostgreSQL INSERT ... ON CONFLICT DO NOTHING atomic handling
- ✅ Race condition protection at millisecond level
- ✅ No lost updates, no duplicate transactions

---

## 🔍 MONITORING & OBSERVABILITY

### Actuator Health Checks

#### Gateway Service Health
```bash
# Basic health
curl http://localhost:8080/actuator/health | jq .

# Detailed health (requires appropriate permissions)
curl http://localhost:8080/actuator/health/liveness | jq .
curl http://localhost:8080/actuator/health/readiness | jq .
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "kafka": {"status": "UP"},
    "redis": {"status": "UP"},
    "discoveryClient": {"status": "UP"}
  }
}
```

#### All Services Health
```bash
for service in 8080 8081 8082 8083; do
  echo "=== Port $service ==="
  curl -s http://localhost:$service/actuator/health | jq .status
done
```

### Metrics & Performance

#### Request Metrics
```bash
# See HTTP request counts, latencies
curl http://localhost:8080/actuator/metrics/http.server.requests | jq .

# See specific endpoint latency
curl "http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/api/accounts" | jq .
```

#### JVM Metrics
```bash
# Memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq .

# Thread count
curl http://localhost:8080/actuator/metrics/jvm.threads.live | jq .

# GC activity
curl http://localhost:8080/actuator/metrics/jvm.gc.pause | jq .
```

#### Database Connection Pool
```bash
# HikariCP pool stats
curl "http://localhost:8082/actuator/metrics/hikaricp.connections" | jq .

# Active connections
curl "http://localhost:8082/actuator/metrics/hikaricp.connections?tag=state:active" | jq .
```

### Structured Logging & Correlation IDs

#### View Logs with Correlation ID
```bash
# From docker-compose output, search for correlation ID
docker-compose logs | grep "correlationId"

# Or get a specific transaction's full trace:
docker-compose logs | grep "550e8400-e29b-41d4-a716-446655440002"
```

**What You'll See:**
```
ledger_gateway  | 2026-06-21T06:10:00.000Z [8c3e9c1d-4f2a-11ec-81d8-0242ac110002] INFO ...
ledger_auth     | 2026-06-21T06:10:00.500Z [8c3e9c1d-4f2a-11ec-81d8-0242ac110002] INFO ...
ledger_ledger   | 2026-06-21T06:10:01.000Z [8c3e9c1d-4f2a-11ec-81d8-0242ac110002] INFO ...
```

All operations for that single request carry the same correlation ID.

---

## 📡 KAFKA & EVENT STREAMING

### Access Kafka CLI Tools (Inside Container)
```bash
# List topics
docker exec ledger_kafka kafka-topics --bootstrap-server localhost:9092 --list

# Expected output:
# transaction-settled
# transaction-settled.DLT (created after first failure)
```

### Monitor Events
```bash
# Start a consumer to see all events in real-time
docker exec ledger_kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic transaction-settled \
  --from-beginning

# After posting a transaction, you should see event payload:
# {
#   "transactionId": "550e8400-e29b-41d4-a716-446655440002",
#   "amount": 1000,
#   "currency": "INR",
#   "sourceAccountId": "550e8400-e29b-41d4-a716-446655440000",
#   "destinationAccountId": "550e8400-e29b-41d4-a716-446655440001",
#   "status": "SETTLED",
#   "correlationId": "8c3e9c1d-4f2a-11ec-81d8-0242ac110002",
#   "timestamp": 1782021601000
# }
```

**What This Tests:**
- ✅ Transaction events published to Kafka
- ✅ Event carries correlation ID for tracing
- ✅ Notification Service can consume and process

### Dead Letter Topic (For Failures)
```bash
# If notification consumer fails 3 times, message goes here:
docker exec ledger_kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic transaction-settled.DLT \
  --from-beginning
```

---

## 📚 SWAGGER & API DOCUMENTATION

### Access Swagger UI

**⭐ Use these URLs in your browser:**

| Service | URL |
|---------|-----|
| **Auth Service** | [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) |
| **Ledger Service** | [http://localhost:8082/swagger-ui/index.html](http://localhost:8082/swagger-ui/index.html) |

**Note:** Swagger UI is accessed directly at each service (not through the gateway). This allows you to test the API with proper authentication tokens.

### Features You'll See
1. **Endpoint listing** — all HTTP operations documented
2. **Try it out** — make real requests directly in the UI
3. **Request/Response schemas** — see what fields are required
4. **Error codes** — what each endpoint can return (400, 401, 409, etc.)
5. **Authentication** — "Authorize" button to add Bearer token

### Example Workflow in Swagger
1. POST /auth/register
   - Click "Try it out"
   - Enter username, email, password
   - Click "Execute"
   - Copy the `accessToken` from response

2. Click the "Authorize" button
   - Paste: `Bearer <accessToken>`
   - Click "Authorize"

3. Try protected endpoints
   - POST /api/accounts
   - GET /api/accounts/{id}
   - GET /api/accounts/{id}/balance
   - All now work with your token

---

## 🗄️ DATABASE VERIFICATION

### Connect to PostgreSQL
```bash
# Connect to auth database
docker exec -it ledger_postgres psql -U ledger -d auth_db

# Connect to ledger database
docker exec -it ledger_postgres psql -U ledger -d ledger_db
```

### Check Users Table (Auth Service)
```sql
-- Inside psql for auth_db
SELECT id, username, email, is_active, created_at FROM users;

-- Check password hash (should be BCrypt hash, not plaintext)
SELECT id, username, password_hash FROM users WHERE username = 'alice';

-- Expected: $2a$10$... (BCrypt format)
```

### Check Accounts Table (Ledger Service)
```sql
-- Inside psql for ledger_db
SELECT id, name, type, currency, is_active, version, created_at FROM accounts;
```

### Check Journal Entries (Immutable Ledger)
```sql
-- Inside psql for ledger_db
SELECT id, transaction_id, account_id, entry_type, amount, created_at FROM journal_entries;

-- Verify immutability: updated_at should be NULL
SELECT id, created_at, updated_at FROM journal_entries;
-- Expected: all updated_at are NULL
```

### Check Transactions
```sql
-- Inside psql for ledger_db
SELECT id, amount, currency, status, idempotency_key, created_at, settled_at FROM transactions;

-- Verify idempotency keys are unique
SELECT idempotency_key, COUNT(*) FROM transactions GROUP BY idempotency_key HAVING COUNT(*) > 1;
-- Expected: empty result (no duplicates)
```

### Check Idempotency Keys Table
```sql
-- Inside psql for ledger_db
SELECT key, http_status_code, created_at, expires_at FROM idempotency_keys;

-- Verify the cache works: same key should exist only once
SELECT COUNT(*) FROM idempotency_keys WHERE key = 'unique-key-12345';
-- Expected: 1
```

---

## ⚡ PERFORMANCE TESTING

### Load Test Single Endpoint
```bash
# Simple load test (requires ab tool or curl loop)
# 100 concurrent balance queries
for i in {1..100}; do
  curl http://localhost:8082/api/accounts/550e8400-e29b-41d4-a716-446655440000/balance \
    -H "Authorization: Bearer <your-token>" &
done
wait

# Check metrics after:
curl http://localhost:8080/actuator/metrics/http.server.requests | jq .
```

**What This Tests:**
- ✅ Cache hit ratio (balance should be cached)
- ✅ Connection pool handling
- ✅ Database query performance
- ✅ Response latency under load

### Monitor Resource Usage
```bash
# Watch Docker resource usage during load test
docker stats ledger_ledger ledger_postgres

# Look for:
# - CPU: should not spike beyond 50% per container
# - Memory: Postgres should be < 256MB
# - Java services should be < 512MB
```

---

## 🔒 SECURITY TESTING

### Authentication Bypass Attempts
```bash
# Should all fail with 401 Unauthorized

# No token
curl http://localhost:8082/api/accounts

# Invalid token
curl http://localhost:8082/api/accounts \
  -H "Authorization: Bearer invalid-token-here"

# Expired token (manually set exp to past time using jwt.io)

# Modified token (change the signature using jwt.io)
```

### SQL Injection Tests
```bash
# Should sanitize input, not throw SQL error
curl "http://localhost:8082/api/accounts?name='; DROP TABLE accounts; --" \
  -H "Authorization: Bearer <your-token>"

# Should return 404 or validation error, never a SQL error
```

### Password Strength
```bash
# Try to register with weak password
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "weakuser", "email": "weak@example.com", "password": "123"}'

# Should fail with 400 Bad Request (password too weak)
```

---

## 📊 ENTERPRISE SKILLS & BEST PRACTICES

### 1. Observability Architecture
**What:** Logs, metrics, traces tied by correlation ID
- Every request gets a correlation ID at the gateway
- Correlation ID flows through all services
- Search logs by correlation ID to debug a single flow

**Why:** In production, you can't debug by looking at code. You debug by following the user's request through all services and seeing where it broke.

**Test It:**
```bash
# 1. Make a request
curl http://localhost:8082/api/accounts \
  -H "Authorization: Bearer <token>"

# 2. Get the correlation ID from response headers
curl -v http://localhost:8082/api/accounts \
  -H "Authorization: Bearer <token>" 2>&1 | grep X-Correlation-ID

# 3. Grep all logs for that ID
docker-compose logs | grep "8c3e9c1d-4f2a-11ec-81d8-0242ac110002"

# You'll see every log line from every service for that ONE request
```

### 2. Distributed Tracing & MDC (Mapped Diagnostic Context)
**What:** Every log line automatically includes request context
- User ID
- Correlation ID
- Transaction ID
- Operation being performed

**Why:** In a monolith, you have stack traces. In microservices, you have correlation IDs. They serve the same debugging purpose.

**Test It:**
```bash
# Look at raw logs:
docker-compose logs ledger_ledger | head -20

# Each line will look like:
# 2026-06-21T06:10:00.000Z [8c3e9c1d-4f2a-11ec-81d8-0242ac110002] INFO TransactionService - Creating transaction
# ↑ timestamp                ↑ correlation ID                    ↑ level, class, message

# vs without MDC (useless in microservices):
# 2026-06-21T06:10:00.000Z INFO TransactionService - Creating transaction
# Now you don't know which user, which request, which transaction this is
```

### 3. API Gateway Pattern
**What:** Single entry point for all client requests
- Centralizes authentication
- Adds/injects correlation IDs
- Routes to services by name (via Eureka)
- Could add rate limiting, request logging, etc.

**Why:** Clients don't talk directly to services (http://ledger-service:8082). Clients talk to gateway (http://localhost:8080). Gateway handles auth, routing, and observability.

**Test It:**
```bash
# This fails (no direct service access from outside)
curl http://localhost:8082/api/accounts

# This works (through gateway)
curl http://localhost:8080/api/accounts

# Gateway's routing rule:
# Request to /api/* → routes to ledger-service:8082
# Request to /auth/* → routes to auth-service:8081
```

### 4. Idempotency & Race Conditions
**What:** The same API call twice = same result, not two side effects
- First call: creates transaction, stores idempotency key
- Duplicate call: returns stored response from first call
- Even if both calls arrive at same millisecond, exactly ONE transaction is created

**Why:** Network timeouts are common. Client retries. Without idempotency, a timeout on a payment request could create multiple transactions. With idempotency, the retry safely returns the original response.

**Test It:**
```bash
# Send the request
RESPONSE1=$(curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: test-123" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId": "...", "destinationAccountId": "...", "amount": 100, "currency": "INR", "idempotencyKey": "test-123"}')

TXN_ID=$(echo $RESPONSE1 | jq -r .id)
echo "Created transaction: $TXN_ID"

# Send it again
RESPONSE2=$(curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: test-123" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId": "...", "destinationAccountId": "...", "amount": 100, "currency": "INR", "idempotencyKey": "test-123"}')

TXN_ID2=$(echo $RESPONSE2 | jq -r .id)
echo "From retry: $TXN_ID2"

# If they match:
# ✅ PASS: Idempotency working. Same request = same transaction ID.
# ❌ FAIL: Different IDs = idempotency broken (two transactions created).
```

### 5. Double-Entry Bookkeeping
**What:** Every transfer creates two journal entries (DEBIT + CREDIT) as ONE atomic operation
- If only DEBIT is created and app crashes, database is corrupt
- If both are created but somehow unbalanced, books don't balance
- @Transactional ensures atomicity

**Why:** This is THE core rule of accounting. It ensures no money is created or destroyed. Every debit has a matching credit.

**Test It:**
```bash
# Post a transfer
curl -X POST http://localhost:8082/api/transactions \
  -H "Authorization: Bearer <token>" \
  -d '{"sourceAccountId": "abc", "destinationAccountId": "def", "amount": 100, ...}'

# Check balances
ALICE=$(curl http://localhost:8082/api/accounts/abc/balance | jq .balance)
BOB=$(curl http://localhost:8082/api/accounts/def/balance | jq .balance)

# Calculate net change (should always be zero)
NET=$((ALICE + BOB))
echo "Net change: $NET"

# If NET = 0: ✅ PASS (money is conserved)
# If NET ≠ 0: ❌ FAIL (money was created or destroyed)
```

### 6. State Machine Enforcement
**What:** Transactions follow a valid path (PENDING → SETTLED → REVERSED)
- Invalid transitions are rejected at the database/service layer
- You can't accidentally create a FAILED transaction that tries to SETTLE

**Why:** State machines prevent impossible states. In fintech, impossible states = bugs that lose or create money.

**Test It:**
```bash
# Try to reverse an already-reversed transaction
curl -X POST http://localhost:8082/api/transactions/$TXN_ID/reverse \
  -H "Authorization: Bearer <token>"

# Should fail: 400 Bad Request - "Only SETTLED transactions can be reversed"
# Not: 200 OK (which would be bad — states shouldn't allow invalid transitions)
```

### 7. Database Immutability (Write-Once Ledger)
**What:** Journal entries can never be updated or deleted
- You can only INSERT and SELECT
- The is_immutable check or lack of UPDATE/DELETE columns enforces this
- Original transaction record remains forever (for audit)

**Why:** In accounting, audit trails matter. You need to know the exact history. If entries can be updated, the audit trail is worthless.

**Test It:**
```sql
-- Try to update a journal entry (this should fail or not work)
UPDATE journal_entries SET amount = 2000 WHERE id = '550e8400-e29b-41d4-a716-446655440000';

-- Should fail with: "permission denied" or similar

-- Or the column simply doesn't have an updated_at:
SELECT id, created_at, updated_at FROM journal_entries LIMIT 1;
-- Expected: updated_at is NULL or doesn't exist
```

### 8. Async Event-Driven Architecture
**What:** Transaction settlement publishes an event to Kafka
- Ledger Service doesn't wait for Notification Service
- If Notification Service is down, transactions still settle
- When Notification Service recovers, it processes queued events

**Why:** Coupling kills scalability. In production, notification could be slow (calling external email API). If Ledger waited for it, payment API would be slow. By decoupling with Kafka, Ledger returns instantly, Notification catches up later.

**Test It:**
```bash
# 1. Stop notification service
docker-compose stop notification-service

# 2. Post a transaction (should still succeed and settle)
curl -X POST http://localhost:8082/api/transactions ...

# 3. Check Kafka topic (event should be there, waiting for consumer)
docker exec ledger_kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic transaction-settled --from-beginning

# 4. Restart notification service
docker-compose up -d notification-service

# 5. Notification service reads the queued event and processes it
```

### 9. Graceful Degradation (Dead Letter Topics)
**What:** If Notification Service fails 3 times, the event goes to a Dead Letter Topic (DLT)
- Original event is preserved (not lost)
- Ops team can manually inspect and replay
- The original transaction is unaffected (already settled)

**Why:** Production databases are messy. Sometimes the phone number is invalid, sometimes the email service is down. DLT ensures no data loss while keeping the main flow fast.

**Test It:**
```bash
# 1. Configure notification consumer to always fail (inject an exception)
# 2. Post a transaction (publishes event)
# 3. Watch Kafka consumer retry 3 times and fail
# 4. Check DLT for the failed event
docker exec ledger_kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic transaction-settled.DLT --from-beginning
```

### 10. Service Discovery (Eureka)
**What:** Services register themselves when they start, clients ask Eureka for addresses
- No hardcoded URLs like http://ledger-service:8082
- When a service restarts, clients automatically find it at its new address
- Enables horizontal scaling (spin up 3 ledger-service instances, Eureka knows about all)

**Why:** In production, services restart, get new IPs, scale in/out. Hardcoded URLs don't work at that scale.

**Test It:**
```bash
# 1. Visit Eureka dashboard
open http://localhost:8761

# You'll see:
# LEDGER-SERVICE (2 instances)
# AUTH-SERVICE (1 instance)
# NOTIFICATION-SERVICE (1 instance)
# GATEWAY-SERVICE (1 instance)

# 2. Stop a service
docker-compose stop ledger-service

# 3. Eureka marks it DOWN (within 30 seconds)

# 4. Restart it
docker-compose up -d ledger-service

# 5. Eureka marks it UP again
```

---

## 📋 COMPLETE TEST CHECKLIST

Before declaring Features 1-5 complete, verify all of these work:

### Feature 1: Auth
- [ ] Register new user
- [ ] Login with correct password
- [ ] Login fails with wrong password
- [ ] JWT token can be decoded
- [ ] Token expires after 24 hours
- [ ] Refresh token generates new access token
- [ ] Duplicate username fails

### Feature 2: Accounts
- [ ] Create account
- [ ] Get account details
- [ ] Get account balance (returns 0 for new account)
- [ ] Cannot create account without token
- [ ] Cannot create account with invalid currency

### Feature 3: Double-Entry Transactions
- [ ] Post a 1000 INR transfer from Account A to B
- [ ] Account A balance decreases by 1000
- [ ] Account B balance increases by 1000
- [ ] Journal entries created (1 DEBIT, 1 CREDIT)
- [ ] Transaction status is SETTLED
- [ ] Cannot transfer more than available balance
- [ ] Cannot transfer 0 amount
- [ ] Cannot transfer with mismatched currency

### Feature 4: State Machine
- [ ] Post and reverse a transaction
- [ ] Original transaction marked as REVERSED
- [ ] Balances return to pre-transfer state
- [ ] Cannot reverse already-reversed transaction
- [ ] Cannot reverse PENDING transaction (if state is enforced)

### Feature 5: Idempotency
- [ ] Post transaction with Idempotency-Key
- [ ] Retry with same key returns same transaction ID
- [ ] Only ONE transaction exists in database
- [ ] Concurrent requests with same key result in ONE transaction
- [ ] Balances correct (money not duplicated)

### Observability
- [ ] Actuator health endpoints return UP
- [ ] Correlation ID present in response headers
- [ ] Correlation ID in logs for entire request trace
- [ ] Metrics endpoint returns valid data

### Kafka
- [ ] Events published to transaction-settled topic
- [ ] Notification Service consumes events
- [ ] Events carry correlation ID

### Database
- [ ] BCrypt passwords stored (not plaintext)
- [ ] Journal entries never updated or deleted
- [ ] Idempotency keys prevent duplicates
- [ ] Transactions have immutable status history

---

## 🎯 WHAT THIS PREPARES YOU FOR

When an interviewer asks: *"Walk me through your payment system"*

You can now say:

> I built a microservices payment ledger with 5 services: Auth, Ledger, Notification, Gateway, and Eureka discovery. 
>
> **The flow:** Client hits the Gateway (single entry point), which injects a correlation ID and validates the JWT. Auth Service handles login/register with BCrypt + JWT tokens. Ledger Service accepts transfers and writes double-entry journal entries atomically. If the same request retries (network timeout), idempotency protection returns the original response without duplicating the transaction.
>
> **Consistency guarantees:** Transactions are atomic (@Transactional). Journal entries are immutable (write-once ledger). Every transfer balances (debits = credits). State transitions are enforced (can't reverse a non-settled transaction).
>
> **Scale & resilience:** Services discover each other through Eureka (not hardcoded URLs). Transaction settlement publishes to Kafka asynchronously (Notification Service doesn't block payments). If Notification fails, events go to a Dead Letter Topic (no data loss).
>
> **Observability:** Every request carries a correlation ID through all logs. You can search by that ID and see the entire lifecycle across services