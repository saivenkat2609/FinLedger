# Circuit Breaker Implementation — Feature 10.1

## Overview

This implementation adds Resilience4j circuit breaker protection to the API Gateway. The circuit breaker prevents cascading failures by:

1. **Monitoring** downstream service calls
2. **Opening the circuit** when failures exceed threshold
3. **Rejecting requests immediately** while circuit is open (fast-fail)
4. **Attempting recovery** after a timeout by moving to HALF-OPEN state
5. **Closing the circuit** when the service recovers

## Architecture

```
Request → Gateway → Circuit Breaker → Downstream Service
           ↓
    (Circuit OPEN?)
           ↓
    Reject with 503 (fast-fail)
           ↓
    OR
           ↓
    Route to service
```

## Configuration Details

### Circuit Breaker States

| State | Behavior | Duration |
|-------|----------|----------|
| **CLOSED** | All requests pass through | Normal operation |
| **OPEN** | All requests rejected with 503 | 30 seconds |
| **HALF-OPEN** | Limited test requests allowed | Up to 3 concurrent requests |

### Thresholds (per service)

- **Failure Rate Threshold**: 50% (if 5 of 10 recent calls fail, open circuit)
- **Slow Call Rate Threshold**: 50% (if 5 of 10 calls are slow, count as failures)
- **Slow Call Duration**: 2000ms (anything slower is "slow")
- **Sliding Window**: Last 10 calls

### Retry Configuration

- **Max Attempts**: 3 retries
- **Wait Duration**: 1 second between retries
- **Backoff Strategy**: Exponential (1s → 2s → 4s)
- **Retryable Exceptions**:
  - `ConnectException` (network unavailable)
  - `TimeoutException` (service slow)
  - `ResourceAccessException` (connection lost)

### Time Limiter

- **Timeout**: 5 seconds per request
- **Auto-cancellation**: Yes (cancel long-running operations)

## Testing the Circuit Breaker

### Test 1: Normal Operation (CLOSED Circuit)

```bash
# All requests succeed
for i in {1..5}; do
  curl -X GET http://localhost:8080/api/accounts \
    -H "Authorization: Bearer <JWT_TOKEN>" \
    -w "\nStatus: %{http_code}\n"
  sleep 1
done

# Expected: 200 OK responses
```

### Test 2: Triggering Failures (Moving to OPEN)

```bash
# Stop the ledger-service
docker-compose stop ledger-service

# Send requests rapidly to trigger failures
for i in {1..15}; do
  curl -X GET http://localhost:8080/api/accounts \
    -H "Authorization: Bearer <JWT_TOKEN>" \
    -w "\nStatus: %{http_code}\n"
done

# Expected behavior:
# - Requests 1-5: Timeout errors (service down, retries exhaust)
# - Requests 6-15: Immediate 503 (circuit is OPEN)
# - Response includes: "circuit breaker is open"
```

### Test 3: Automatic Recovery (HALF-OPEN → CLOSED)

```bash
# Restart ledger-service
docker-compose start ledger-service

# Wait 30+ seconds for circuit to enter HALF-OPEN state
sleep 35

# Send a request (this is a probe)
curl -X GET http://localhost:8080/api/accounts \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -w "\nStatus: %{http_code}\n"

# Expected: 200 OK (probe succeeds)
# Circuit automatically closes

# Subsequent requests should succeed
for i in {1..5}; do
  curl -X GET http://localhost:8080/api/accounts \
    -H "Authorization: Bearer <JWT_TOKEN>" \
    -w "\nStatus: %{http_code}\n"
done

# Expected: All 200 OK
```

### Test 4: Observing Metrics

```bash
# View circuit breaker health and metrics
curl -s http://localhost:8080/actuator/health | jq '.'

# Expected output includes:
# {
#   "status": "UP",
#   "components": {
#     "circuitBreakers": {
#       "status": "UP",
#       "details": {
#         "ledger-service": {
#           "status": "UP",
#           "details": {
#             "state": "CLOSED"
#           }
#         }
#       }
#     }
#   }
# }

# View circuit breaker metrics
curl -s http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls | jq '.'

# Expected: call counts, success rates, state transitions
```

## Logs Example

When a circuit breaker opens, you'll see:

```
2026-06-23 10:15:23 [abc-123] WARN GatewayApplication - CircuitBreaker 'ledger-service' registered
2026-06-23 10:15:25 [def-456] WARN ResilienceConfig - CircuitBreaker 'ledger-service' state changed from CLOSED to OPEN
2026-06-23 10:15:26 [ghi-789] WARN FallbackController - [xyz-999] Circuit breaker is OPEN for service: ledger-service. Returning 503.
2026-06-23 10:15:56 [jkl-111] INFO ResilienceConfig - CircuitBreaker 'ledger-service' state changed from OPEN to HALF_OPEN
2026-06-23 10:15:56 [mno-222] INFO ResilienceConfig - CircuitBreaker 'ledger-service' state changed from HALF_OPEN to CLOSED
```

## Services Protected

Each of these services has its own circuit breaker:

| Service | Route | Timeout | Retry Attempts |
|---------|-------|---------|-----------------|
| auth-service | `/auth/**` | 5s | 3 |
| ledger-service | `/api/**` | 5s | 3 |
| reporting-service | `/reports/**` | 5s | 3 |

## Response When Circuit Is Open

```json
{
  "timestamp": "2026-06-23T10:15:26Z",
  "status": 503,
  "error": "Service Unavailable",
  "message": "The downstream service (ledger-service) is temporarily unavailable. The circuit breaker is open. Please retry after 30 seconds.",
  "path": "/api/accounts",
  "correlationId": "abc-123-def"
}
```

## Production Considerations

### Rate Limiting Integration

The circuit breaker works alongside rate limiting:
- Rate limiting rejects excessive requests (429)
- Circuit breaker protects from cascading failures (503)

Example flow:
```
User hammers /api/accounts
  → Rate limit exceeded → 429 Too Many Requests
  → User backs off
  → Next request succeeds

Service becomes slow
  → 3 retries with backoff
  → Failure rate > 50% → Circuit opens
  → Requests immediately fail → 503 Service Unavailable
  → Client retries after 30s
  → Service recovered → Circuit closes → Success
```

### Fallback Strategies (Future Enhancement)

For critical operations, you can add fallback methods that return cached data:

```java
@GetMapping("/api/accounts/{id}/balance")
@CircuitBreaker(name = "ledger-service", fallbackMethod = "cachedBalance")
public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID id) {
    return ledgerService.getBalance(id);
}

public ResponseEntity<BalanceResponse> cachedBalance(UUID id, Exception e) {
    // Return cached balance from Redis with a "STALE" flag
    return ResponseEntity.ok(cacheService.getCachedBalance(id, "STALE"));
}
```

## Key Metrics to Monitor

```bash
# Total calls made (success + failure)
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls

# Total slow calls
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls.slow

# State transitions (CLOSED → OPEN → HALF_OPEN → CLOSED)
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# Call duration (latency)
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.call.duration
```

## Troubleshooting

### "Circuit breaker is open" but service is healthy

**Cause**: Circuit may still be waiting for the 30-second timeout to enter HALF-OPEN state.

**Fix**: 
1. Wait 30+ seconds
2. Or manually close: check service logs, ensure it's actually healthy
3. Increase `waitDurationInOpenState` in config if 30s is too aggressive

### Too many retries causing timeouts

**Cause**: Retries with backoff (1s, 2s, 4s = 7 seconds total) plus 5s timeout = 12 seconds per request.

**Fix**:
1. Reduce `maxAttempts` from 3 to 2
2. Reduce `timeoutDuration` from 5s to 3s
3. Use `intervalFunction: linearBackoff` instead of exponential

### Circuit bounces between OPEN/HALF-OPEN

**Cause**: Service is flaky (some requests fail, some succeed). Probe requests keep failing.

**Fix**:
1. Investigate the actual service (high load? resource exhaustion?)
2. Increase `failureRateThreshold` to 60% (less aggressive)
3. Increase `permittedNumberOfCallsInHalfOpenState` to 5 (more probes before deciding)

## Comparison: With vs Without Circuit Breaker

### Without Circuit Breaker
```
T=0ms:  Request 1 → ledger-service (DOWN) → timeout after 5s → fail
T=5s:   Request 2 → ledger-service (DOWN) → timeout after 5s → fail
T=10s:  Request 3 → ledger-service (DOWN) → timeout after 5s → fail
...
T=300s: Request 60 → ledger-service (RECOVERED) → succeed

Result: 60 wasted requests, 300 seconds of pain, user gives up
```

### With Circuit Breaker
```
T=0ms:   Request 1 → ledger-service (DOWN) → timeout after 5s → fail
T=5s:    Request 2 → ledger-service (DOWN) → timeout after 5s → fail
T=10s:   Request 3 → ledger-service (DOWN) → timeout after 5s → fail
T=15s:   Request 4 → Circuit OPEN (after 3 failures) → 503 immediately
T=16ms:  Request 5 → Circuit OPEN → 503 immediately (1ms response)
...
T=45s:   Wait 30s, enter HALF_OPEN
T=46s:   Probe request → ledger-service (RECOVERED) → succeed
T=47s:   Request 6 → Circuit CLOSED → succeed

Result: 5 failed requests, ~46 seconds total, user retries and succeeds
```

## Feature 10.1 Complete ✅

- ✅ Resilience4j circuit breaker added to gateway
- ✅ All inter-service calls wrapped with circuit breaker
- ✅ Automatic retry with exponential backoff
- ✅ Time limiter to prevent hanging requests
- ✅ Fallback endpoint for 503 Service Unavailable responses
- ✅ Health indicators exposed at /actuator/health
- ✅ Metrics available for monitoring
- ✅ Correlation IDs flow through circuit breaker responses

---

## Next: Feature 10.2 (Retry Logic Details)

Already implemented above, but here's what it does:

1. **Transient failures** (connection timeout, network glitch) → **Retry 3 times**
2. **Each retry waits**: 1s, then 2s, then 4s (exponential backoff)
3. **Permanent failures** (5xx errors, timeouts after retries) → **Open circuit**
4. **Circuit recovers** after 30s → Probe with 1 test request → Close on success

This ensures the system is resilient to temporary glitches while fast-failing on persistent issues.
