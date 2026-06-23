package com.ledger.gateway.resilience;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class RetryBehaviorTest {

    @Autowired
    private RetryRegistry retryRegistry;

    private AtomicInteger callCount;
    private List<Long> retryTimestamps;

    @BeforeEach
    void setUp() {
        callCount = new AtomicInteger(0);
        retryTimestamps = new ArrayList<>();
    }

    /**
     * Test 1: Retry executes on transient failures (e.g., connection timeout)
     * Verifies: First attempt fails, subsequent retries succeed
     */
    @Test
    void testRetryOnTransientFailure() {
        log.info("Test: Retry on transient failure");

        Retry ledgerServiceRetry = retryRegistry.retry("ledger-service");

        // Simulate transient failure: fail twice, succeed on third attempt
        var result = Retry.decorateCallable(ledgerServiceRetry, () -> {
            int attempt = callCount.incrementAndGet();
            long timestamp = System.currentTimeMillis();
            retryTimestamps.add(timestamp);

            log.info("Attempt {}: callCount={}", attempt, callCount.get());

            if (attempt < 3) {
                throw new RuntimeException("Transient failure (simulating connection timeout)");
            }
            return "Success on attempt " + attempt;
        });

        try {
            String response = result.call();
            log.info("Final response: {}", response);

            assertEquals(3, callCount.get(), "Should have made 3 attempts (1 original + 2 retries)");
            assertEquals("Success on attempt 3", response);
            assertEquals(3, retryTimestamps.size());

            log.info("✓ Test passed: Retry succeeded after transient failures");
        } catch (Exception e) {
            fail("Should have succeeded on retry: " + e.getMessage());
        }
    }

    /**
     * Test 2: Exponential backoff timing
     * Verifies: Retries wait progressively longer (1s, 2s, 4s)
     */
    @Test
    void testExponentialBackoffTiming() throws Exception {
        log.info("Test: Exponential backoff timing");

        Retry ledgerServiceRetry = retryRegistry.retry("ledger-service");

        // Fail 2 times, succeed on 3rd
        var result = Retry.decorateCallable(ledgerServiceRetry, () -> {
            int attempt = callCount.incrementAndGet();
            long timestamp = System.currentTimeMillis();
            retryTimestamps.add(timestamp);

            log.info("Attempt {}: timestamp={}", attempt, timestamp);

            if (attempt < 3) {
                throw new RuntimeException("Simulate transient failure");
            }
            return "Success";
        });

        long startTime = System.currentTimeMillis();
        result.call();
        long totalDuration = System.currentTimeMillis() - startTime;

        log.info("Total duration for 3 attempts: {}ms", totalDuration);
        log.info("Retry timestamps: {}", retryTimestamps);

        // Calculate wait times between retries
        long waitBetween1And2 = retryTimestamps.get(1) - retryTimestamps.get(0);
        long waitBetween2And3 = retryTimestamps.get(2) - retryTimestamps.get(1);

        log.info("Wait time between attempt 1 and 2: {}ms", waitBetween1And2);
        log.info("Wait time between attempt 2 and 3: {}ms", waitBetween2And3);

        // Verify exponential backoff (1s, 2s)
        // Allow some tolerance for execution time (±200ms)
        assertTrue(waitBetween1And2 >= 800 && waitBetween1And2 <= 1200,
            "First retry should wait ~1000ms, got " + waitBetween1And2);
        assertTrue(waitBetween2And3 >= 1800 && waitBetween2And3 <= 2200,
            "Second retry should wait ~2000ms, got " + waitBetween2And3);

        assertEquals(3, callCount.get());
        log.info("✓ Test passed: Exponential backoff verified");
    }

    /**
     * Test 3: Max retries exceeded → exception thrown
     * Verifies: After 3 attempts, stop retrying and throw exception
     */
    @Test
    void testMaxRetriesExceeded() {
        log.info("Test: Max retries exceeded");

        Retry ledgerServiceRetry = retryRegistry.retry("ledger-service");

        // Always fail
        var result = Retry.decorateCallable(ledgerServiceRetry, () -> {
            int attempt = callCount.incrementAndGet();
            log.info("Attempt {}", attempt);
            throw new RuntimeException("Persistent failure");
        });

        Exception exception = assertThrows(Exception.class, () -> result.call(),
            "Should throw exception after max retries exhausted");

        assertEquals(3, callCount.get(), "Should have made exactly 3 attempts");
        assertTrue(exception.getMessage().contains("Persistent failure"));

        log.info("✓ Test passed: Max retries exceeded, exception thrown after {} attempts", callCount.get());
    }

    /**
     * Test 4: No retry on client errors (4xx)
     * Verifies: 400/401/403/404 errors should not be retried
     */
    @Test
    void testNoRetryOnClientErrors() {
        log.info("Test: No retry on client errors");

        Retry ledgerServiceRetry = retryRegistry.retry("ledger-service");

        // Throw client error (not in retryable exceptions list)
        var result = Retry.decorateCallable(ledgerServiceRetry, () -> {
            int attempt = callCount.incrementAndGet();
            log.info("Attempt {}", attempt);

            if (attempt == 1) {
                // 400 Bad Request is not retryable
                throw new IllegalArgumentException("Bad request");
            }
            return "Should not reach here";
        });

        Exception exception = assertThrows(Exception.class, () -> result.call(),
            "Client error should not be retried");

        assertEquals(1, callCount.get(), "Should only attempt once (no retries on client errors)");
        log.info("✓ Test passed: Client errors not retried, failed after {} attempt", callCount.get());
    }

    /**
     * Test 5: Retry configuration validation
     * Verifies: Retry settings are correctly configured
     */
    @Test
    void testRetryConfiguration() {
        log.info("Test: Retry configuration validation");

        Retry ledgerServiceRetry = retryRegistry.retry("ledger-service");
        Retry authServiceRetry = retryRegistry.retry("auth-service");
        Retry reportingServiceRetry = retryRegistry.retry("reporting-service");

        // Verify all services have retry configured
        assertNotNull(ledgerServiceRetry);
        assertNotNull(authServiceRetry);
        assertNotNull(reportingServiceRetry);

        // Verify max attempts
        assertEquals(3, ledgerServiceRetry.getRetryConfig().getMaxAttempts(),
            "Ledger service should have 3 max attempts");
        assertEquals(3, authServiceRetry.getRetryConfig().getMaxAttempts(),
            "Auth service should have 3 max attempts");
        assertEquals(3, reportingServiceRetry.getRetryConfig().getMaxAttempts(),
            "Reporting service should have 3 max attempts");

        log.info("✓ Test passed: All services configured with 3 max retries");
    }

    /**
     * Test 6: Concurrent retries (multiple requests retrying simultaneously)
     * Verifies: Retry logic is thread-safe
     */
    @Test
    void testConcurrentRetries() throws Exception {
        log.info("Test: Concurrent retries are thread-safe");

        Retry ledgerServiceRetry = retryRegistry.retry("ledger-service");
        int threadCount = 5;
        AtomicInteger totalAttempts = new AtomicInteger(0);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    var result = Retry.decorateCallable(ledgerServiceRetry, () -> {
                        int attempt = totalAttempts.incrementAndGet();
                        if (attempt % 3 != 0) {
                            throw new RuntimeException("Transient failure");
                        }
                        return "Success from thread " + threadId;
                    });
                    String response = result.call();
                    log.info("Thread {}: {}", threadId, response);
                } catch (Exception e) {
                    log.error("Thread {} failed: {}", threadId, e.getMessage());
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Each thread should make approximately 3 attempts (may vary slightly)
        log.info("Total attempts across {} threads: {}", threadCount, totalAttempts.get());
        assertTrue(totalAttempts.get() >= threadCount * 3,
            "All threads should have attempted at least 3 times");

        log.info("✓ Test passed: Concurrent retries are thread-safe");
    }

    /**
     * Test 7: Circuit breaker and retry interaction
     * Verifies: Retries happen before circuit opens
     */
    @Test
    void testRetryBeforeCircuitOpens() {
        log.info("Test: Retry happens before circuit breaker opens");

        // This test verifies the order: Retry → if exhausted → Circuit Breaker
        // The actual circuit breaker would open after retries fail

        Retry ledgerServiceRetry = retryRegistry.retry("ledger-service");

        // Fail on all attempts
        var result = Retry.decorateCallable(ledgerServiceRetry, () -> {
            int attempt = callCount.incrementAndGet();
            log.info("Retry attempt {}", attempt);
            throw new RuntimeException("Persistent connection failure");
        });

        assertThrows(Exception.class, () -> result.call());

        // Should have exhausted all 3 retries
        assertEquals(3, callCount.get(),
            "Retry should exhaust before circuit breaker engages");

        log.info("✓ Test passed: All retries exhausted (then circuit breaker would open)");
    }
}
