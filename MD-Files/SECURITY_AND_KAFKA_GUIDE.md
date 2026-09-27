# FinLedger: Spring Security + JWT + Kafka Implementation Guide

---

## 🔐 Security Architecture

### **Complete Auth Flow**

```
┌─────────────────────────────────────────────────────────────┐
│                          CLIENT                             │
└────────────────────────┬────────────────────────────────────┘
                         │
                    1. Register/Login
                         │
┌────────────────────────▼────────────────────────────────────┐
│              API GATEWAY (8080)                             │
│  - No JWT validation needed for /auth/*                     │
│  - Route to Auth Service                                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│           AUTH SERVICE (8081)                              │
│  POST /auth/register                                       │
│    ├─ Hash password with BCrypt                            │
│    ├─ Save User to DB                                      │
│    ├─ Generate JWT access token (24h expiry)               │
│    ├─ Generate refresh token (7d, save to DB)              │
│    └─ Publish user.registered event to Kafka               │
│                                                            │
│  POST /auth/login                                          │
│    ├─ Find user by username                                │
│    ├─ Compare password with BCrypt hash                    │
│    ├─ Generate JWT tokens                                  │
│    └─ Return { accessToken, refreshToken }                 │
└────────────────────────┬───────────────────────────────────┘
                         │
                    2. Return JWT tokens
                         │
┌────────────────────────▼────────────────────────────────────┐
│                       CLIENT                                 │
│  Stores: accessToken, refreshToken                          │
└────────────────────────┬────────────────────────────────────┘
                         │
            3. Subsequent requests with token
                         │
┌────────────────────────▼────────────────────────────────────┐
│              API GATEWAY (8080)                              │
│  Header: Authorization: Bearer <accessToken>                │
│                                                              │
│  JwtAuthenticationGatewayFilter:                             │
│    ├─ Extract token from header                             │
│    ├─ Validate token (not expired, valid signature)         │
│    ├─ Extract user ID & roles from token claims            │
│    ├─ Reject if invalid (return 401)                        │
│    └─ Add X-User-Id, X-Username to downstream request      │
│                                                              │
│  RateLimitingGatewayFilter:                                  │
│    ├─ Get bucket for user (from Redis)                     │
│    ├─ Try to consume 1 token from bucket                   │
│    ├─ Allow if available, reject with 429 if not           │
│    └─ Bucket refills at configured rate (1000/min)         │
│                                                              │
│  RoleCheckGatewayFilter (for /reports/*):                   │
│    ├─ Extract roles from token                              │
│    ├─ Check if ROLE_ADMIN present                           │
│    └─ Reject with 403 if not authorized                     │
└────────────────────────┬────────────────────────────────────┘
                         │
            4. Route to target service
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌─────────────┐  ┌──────────────┐  ┌───────────┐
│   Ledger    │  │  Reporting   │  │Notific.   │
│  Service    │  │  Service     │  │Service    │
│  (8082)     │  │  (8084)      │  │(8083)     │
└─────────────┘  └──────────────┘  └───────────┘
   Uses JWT Claims:        Uses JWT Claims:
   - User ID               - User ID
   - Roles                 - ROLE_ADMIN check
   - Username              
```

---

## 🎟️ JWT Token Structure

### **Access Token (24 hours)**

```json
Header:
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload:
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // User ID
  "username": "alice",
  "roles": ["ROLE_USER"],
  "iat": 1704067200,  // Issued at
  "exp": 1704153600   // Expires in 24 hours
}

Signature:
HMAC-SHA256(header.payload, secret-key)
```

**Complete JWT example:**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.
eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJ1c2VybmFtZSI6ImFsaWNlIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTcwNDA2NzIwMCwiZXhwIjoxNzA0MTUzNjAwfQ.
signature_here
```

### **Refresh Token (7 days)**

- Shorter token (just sub + exp)
- Stored in DB for revocation
- Can be blacklisted when user logs out
- Never expires unless explicitly revoked

---

## 🔑 Implementation Details

### **1. User Password Storage (BCrypt)**

```java
// Never store plain passwords!
String plainPassword = "password123";

// Hash with BCrypt
String bcryptHash = passwordEncoder.encode(plainPassword);
// Result: $2a$10$L.Cl2LhzXr6V/H5Ht7V.v.jAz5F/s/VgvQPRH2g4r0H/W8QFVpvO

// Verification
boolean matches = passwordEncoder.matches(plainPassword, bcryptHash);
// Returns: true

// Store bcryptHash in DB, never plain password
user.setPasswordHash(bcryptHash);
```

**Why BCrypt?**
- Slow by design (100ms per hash) - prevents brute force
- Uses salt - same password produces different hashes
- Adaptive - can increase cost factor over time

---

## 📨 Kafka Event Flow

### **Example: User Registration with Events**

```
1. POST /auth/register
   ├─ Auth Service creates User
   ├─ Publishes to Kafka topic: user-events
   │  Event: { eventType: "user.registered", userId: "...", email: "..." }
   └─ Returns JWT tokens immediately (doesn't wait for consumers)

2. Notification Service listening on user-events
   ├─ Consumes event asynchronously
   ├─ Sends welcome email (log for now)
   └─ Marks event as processed (idempotent)

3. If Notification Service is down:
   ├─ Messages stay in Kafka queue
   ├─ Service comes back online
   ├─ Consumes all pending messages
   └─ Users still get notified (eventual consistency)
```

### **Kafka Topics Configuration**

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: kafka:9092
    
    # Producer settings
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all  # Wait for all replicas (safest)
      retries: 3
      properties:
        linger.ms: 10  # Batch messages for 10ms (higher throughput)
    
    # Consumer settings
    consumer:
      group-id: notification-service
      auto-offset-reset: earliest  # Start from beginning if no offset
      max-poll-records: 500  # Process 500 at a time
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.ledger.*"
    
    # Listener settings
    listener:
      type: batch  # Process messages in batches
      concurrency: 3  # 3 parallel consumer threads
```

### **Publishing Events (Ledger Service)**

```java
@Service
public class TransactionEventPublisher {
    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    
    public void publishTransactionPosted(Transaction transaction) {
        TransactionEvent event = TransactionEvent.builder()
            .transactionId(transaction.getId())
            .sourceAccountId(transaction.getSourceAccountId())
            .destinationAccountId(transaction.getDestinationAccountId())
            .amount(transaction.getAmount())
            .timestamp(LocalDateTime.now())
            .status("POSTED")
            .build();
        
        // Send to Kafka
        // Key: transaction ID (ensures ordering per transaction)
        // Value: JSON serialized event
        kafkaTemplate.send("transaction-events", 
            transaction.getId().toString(), 
            event);
        
        log.info("Published: transaction.posted");
    }
}
```

### **Consuming Events (Notification Service)**

```java
@Service
public class TransactionEventListener {
    private final ProcessedEventRepository processedEventRepository;
    
    @KafkaListener(
        topics = "transaction-events",
        groupId = "notification-service",
        containerFactory = "batchFactory"
    )
    public void onTransactionEvent(List<TransactionEvent> events) {
        for (TransactionEvent event : events) {
            try {
                // Idempotency: check if already processed
                String eventKey = event.getTransactionId() + "-" + event.getStatus();
                if (processedEventRepository.exists(eventKey)) {
                    log.info("Event already processed, skipping: {}", eventKey);
                    continue;
                }
                
                // Handle event
                if ("POSTED".equals(event.getStatus())) {
                    sendNotification(event.getSourceAccountId(),
                        "Transaction posted: $" + event.getAmount());
                } else if ("SETTLED".equals(event.getStatus())) {
                    sendNotification(event.getSourceAccountId(),
                        "Transaction settled");
                }
                
                // Mark as processed
                processedEventRepository.save(eventKey);
                
            } catch (Exception e) {
                log.error("Error processing event", e);
                // Kafka will retry, or send to Dead Letter Topic
            }
        }
    }
    
    // Dead Letter Topic handler
    @KafkaListener(topics = "transaction-events.DLT")
    public void handleDlt(TransactionEvent event) {
        log.error("DLT: Failed to process event: {}", event.getTransactionId());
        // Alert ops team
    }
}
```

---

## 🔄 Request Flow with All Components

```
Client Request: GET /api/accounts

1. Request hits API Gateway (8080)
   ├─ CorrelationIdFilter
   │  └─ Generate correlationId: "abc-123"
   │  └─ Add to MDC (all logs will include it)
   │
   ├─ JwtAuthenticationGatewayFilter
   │  ├─ Extract token from Authorization header
   │  ├─ Call JwtTokenProvider.validateToken(token)
   │  ├─ If invalid → return 401 "Unauthorized"
   │  └─ If valid → extract userId, add to headers: X-User-Id, X-Username
   │
   ├─ RateLimitingGatewayFilter
   │  ├─ Get bucket for user from Redis
   │  ├─ Try to consume 1 token
   │  ├─ If available → continue
   │  └─ If not available → return 429 "Too Many Requests"
   │
   └─ Route to Ledger Service (lb://ledger-service)
      (Load balancer automatically finds service via Eureka)

2. Ledger Service receives request
   ├─ Extract X-User-Id from headers
   ├─ Log with correlationId: "[abc-123] Fetching accounts for user: user-123"
   ├─ Query database: SELECT * FROM accounts WHERE user_id = ?
   ├─ Cache result in Redis for 5 minutes
   └─ Return response with accounts

3. Response goes back through Gateway
   ├─ Add response headers: X-Correlation-ID: abc-123
   └─ Return to client

Total flow:
Request → Gateway (auth + rate limit) → Service → DB/Cache → Response
```

---

## 🛡️ Security Best Practices Implemented

✅ **Password Security**
- BCrypt hashing (not plain text, not MD5/SHA1)
- Salted (different hash per password)
- Adaptive cost factor

✅ **Token Security**
- JWT signed (HMAC-SHA256)
- Short expiry (24h access token)
- Refresh tokens stored in DB (can revoke)
- Token validation at Gateway (centralized)

✅ **API Security**
- Rate limiting per user (1000 req/min)
- CORS disabled (change in production)
- CSRF disabled (stateless JWT)
- All endpoints require auth except /auth/*

✅ **Encryption in Transit**
- TLS/HTTPS (in production)
- All service-to-service communication signed

✅ **Least Privilege**
- ROLE_USER for regular operations
- ROLE_ADMIN for sensitive operations
- Services only access what they need

---

## 📊 Monitoring & Debugging

### **Logs with Correlation ID**

```
[2024-01-15 10:30:45] [abc-123] INFO  GatewayFilter - JWT validation passed
[2024-01-15 10:30:45] [abc-123] INFO  LedgerService - Fetching accounts
[2024-01-15 10:30:45] [abc-123] DEBUG AccountRepository - SELECT * FROM accounts WHERE user_id = ?
[2024-01-15 10:30:45] [abc-123] DEBUG CacheService - Cache hit for account-balance
[2024-01-15 10:30:46] [abc-123] INFO  GatewayFilter - Response: 200 OK
```

All logs from one request share `[abc-123]` - can trace entire flow.

### **Actuator Endpoints**

```
GET /actuator/health
  ↓ Returns auth-service UP, ledger-service UP, kafka UP

GET /actuator/metrics
  ↓ http.server.requests.count: 1523
  ↓ http.server.requests: 45ms (avg)
  ↓ db.postgresql.connections.active: 12

GET /actuator/prometheus
  ↓ Prometheus scrape format (for monitoring dashboards)
```

---

## 🚀 Deployment Checklist

- [ ] Store JWT secret in environment variable (not in code)
- [ ] Enable HTTPS/TLS in production
- [ ] Set Redis password (not empty in prod)
- [ ] Enable Kafka authentication (SASL/SSL)
- [ ] Configure CORS properly (specify allowed origins)
- [ ] Set up monitoring (Prometheus + Grafana)
- [ ] Enable audit logging (track all auth events)
- [ ] Setup alerts for failed login attempts
- [ ] Rotate JWT secrets periodically
- [ ] Monitor Kafka consumer lag
