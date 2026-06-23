package com.ledger.api.service;

import com.ledger.api.domain.Account;
import com.ledger.api.domain.AccountType;
import com.ledger.api.dto.CreateAccountRequest;
import com.ledger.api.dto.PostTransactionRequest;
import com.ledger.api.repository.AccountRepository;
import com.ledger.api.repository.JournalEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class CacheLoadTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private UUID sourceAccountId;
    private UUID destAccountId;

    @BeforeEach
    void setUp() {
        CreateAccountRequest sourceRequest = new CreateAccountRequest();
        sourceRequest.setName("Test Source Account " + UUID.randomUUID());
        sourceRequest.setType(AccountType.ASSET);
        sourceRequest.setCurrency("INR");

        CreateAccountRequest destRequest = new CreateAccountRequest();
        destRequest.setName("Test Dest Account " + UUID.randomUUID());
        destRequest.setType(AccountType.ASSET);
        destRequest.setCurrency("INR");

        sourceAccountId = accountService.createAccount(sourceRequest).getId();
        destAccountId = accountService.createAccount(destRequest).getId();

        // Seed source account with initial balance
        seedAccountBalance(sourceAccountId, new BigDecimal("10000.00"));
    }

    private void seedAccountBalance(UUID accountId, BigDecimal amount) {
        Account account = accountService.getAccountById(accountId);
        PostTransactionRequest seedRequest = new PostTransactionRequest();
        seedRequest.setSourceAccountId(accountId);
        seedRequest.setDestinationAccountId(accountId);
        seedRequest.setAmount(amount);
        seedRequest.setCurrency("INR");
        seedRequest.setDescription("Seed transaction");
        seedRequest.setIdempotencyKey("seed-" + UUID.randomUUID());

        try {
            transactionService.postTransfer(seedRequest);
        } catch (Exception e) {
            log.warn("Seed transaction failed (expected for self-transfer validation): {}", e.getMessage());
        }
    }

    @Test
    void testConcurrentBalanceReadsReturnSameValue() throws InterruptedException {
        log.info("Starting test: Concurrent balance reads should return same value");

        BigDecimal firstRead = accountService.getAccountBalance(sourceAccountId);
        log.info("First balance read: {}", firstRead);

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<BigDecimal> balanceValues = new HashSet<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    BigDecimal balance = accountService.getAccountBalance(sourceAccountId);
                    balanceValues.add(balance);
                    log.debug("Thread {}: balance = {}", Thread.currentThread().getId(), balance);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    log.error("Error reading balance", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        log.info("100 concurrent reads completed in {}ms", duration);

        assertEquals(0, errorCount.get(), "No errors should occur during concurrent reads");
        assertEquals(1, balanceValues.size(), "All concurrent reads should return the same balance value");
        assertEquals(firstRead, balanceValues.iterator().next(), "Cached balance should match initial read");
        assertTrue(duration < 5000, "100 concurrent reads should complete in < 5 seconds (cache hit optimization)");
    }

    @Test
    void testCacheInvalidationAfterTransfer() throws InterruptedException {
        log.info("Starting test: Cache invalidation after transfer");

        BigDecimal sourceBalanceBefore = accountService.getAccountBalance(sourceAccountId);
        BigDecimal destBalanceBefore = accountService.getAccountBalance(destAccountId);

        log.info("Source balance before transfer: {}", sourceBalanceBefore);
        log.info("Dest balance before transfer: {}", destBalanceBefore);

        PostTransactionRequest transferRequest = new PostTransactionRequest();
        transferRequest.setSourceAccountId(sourceAccountId);
        transferRequest.setDestinationAccountId(destAccountId);
        transferRequest.setAmount(new BigDecimal("500.00"));
        transferRequest.setCurrency("INR");
        transferRequest.setDescription("Test transfer");
        transferRequest.setIdempotencyKey("transfer-" + UUID.randomUUID());

        transactionService.postTransfer(transferRequest);

        BigDecimal sourceBalanceAfter = accountService.getAccountBalance(sourceAccountId);
        BigDecimal destBalanceAfter = accountService.getAccountBalance(destAccountId);

        log.info("Source balance after transfer: {}", sourceBalanceAfter);
        log.info("Dest balance after transfer: {}", destBalanceAfter);

        assertNotEquals(sourceBalanceBefore, sourceBalanceAfter,
            "Source balance should change after transfer");
        assertNotEquals(destBalanceBefore, destBalanceAfter,
            "Destination balance should change after transfer");

        assertEquals(sourceBalanceBefore.subtract(new BigDecimal("500.00")), sourceBalanceAfter,
            "Source balance should decrease by transfer amount");
        assertEquals(destBalanceBefore.add(new BigDecimal("500.00")), destBalanceAfter,
            "Destination balance should increase by transfer amount");
    }

    @Test
    void testCacheCorrectness() {
        log.info("Starting test: Cache correctness (cached value = DB computed value)");

        BigDecimal cachedBalance = accountService.getAccountBalance(sourceAccountId);
        BigDecimal dbComputedBalance = journalEntryRepository.getAccountBalance(sourceAccountId);

        log.info("Cached balance: {}", cachedBalance);
        log.info("DB computed balance: {}", dbComputedBalance);

        assertEquals(dbComputedBalance, cachedBalance,
            "Cached balance must always match database-computed balance");
    }

    @Test
    void testCacheCorrectnessThroughMultipleTransfers() {
        log.info("Starting test: Cache correctness through multiple transfers");

        for (int i = 1; i <= 5; i++) {
            PostTransactionRequest transferRequest = new PostTransactionRequest();
            transferRequest.setSourceAccountId(sourceAccountId);
            transferRequest.setDestinationAccountId(destAccountId);
            transferRequest.setAmount(new BigDecimal("100.00"));
            transferRequest.setCurrency("INR");
            transferRequest.setDescription("Test transfer " + i);
            transferRequest.setIdempotencyKey("transfer-" + i + "-" + UUID.randomUUID());

            transactionService.postTransfer(transferRequest);

            BigDecimal cachedBalance = accountService.getAccountBalance(sourceAccountId);
            BigDecimal dbComputedBalance = journalEntryRepository.getAccountBalance(sourceAccountId);

            log.info("Transfer {}: Cached={}, DB={}", i, cachedBalance, dbComputedBalance);

            assertEquals(dbComputedBalance, cachedBalance,
                "After transfer " + i + ": cached balance must match database balance");
        }
    }

    @Test
    void testCacheLatencyProfile() throws InterruptedException {
        log.info("Starting test: Cache latency profile (p95 < 50ms target)");

        List<Long> latencies = new ArrayList<>();
        int iterations = 1000;

        // Warm up cache
        accountService.getAccountBalance(sourceAccountId);

        // Measure latencies
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            accountService.getAccountBalance(sourceAccountId);
            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;
            latencies.add(latencyMs);
        }

        latencies.sort(Long::compareTo);
        long p50 = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));

        log.info("Latency profile (cache hits):");
        log.info("  P50: {}ms", p50);
        log.info("  P95: {}ms", p95);
        log.info("  P99: {}ms", p99);

        assertTrue(p95 < 50, "P95 latency should be < 50ms for cache hits (target: sub-50ms)");
    }

    @Test
    void testCacheStalenessPrevention() throws InterruptedException {
        log.info("Starting test: Cache staleness prevention");

        BigDecimal initialBalance = accountService.getAccountBalance(sourceAccountId);
        log.info("Initial cached balance: {}", initialBalance);

        // Post transfer
        PostTransactionRequest transferRequest = new PostTransactionRequest();
        transferRequest.setSourceAccountId(sourceAccountId);
        transferRequest.setDestinationAccountId(destAccountId);
        transferRequest.setAmount(new BigDecimal("250.00"));
        transferRequest.setCurrency("INR");
        transferRequest.setDescription("Staleness test transfer");
        transferRequest.setIdempotencyKey("staleness-" + UUID.randomUUID());

        transactionService.postTransfer(transferRequest);

        // Immediately read balance (cache should be evicted and recomputed)
        BigDecimal balanceAfterTransfer = accountService.getAccountBalance(sourceAccountId);
        log.info("Balance after transfer (should be fresh, not stale): {}", balanceAfterTransfer);

        BigDecimal expectedBalance = initialBalance.subtract(new BigDecimal("250.00"));
        assertEquals(expectedBalance, balanceAfterTransfer,
            "Cache should serve fresh value after transfer, not stale cached value");
    }

    @Test
    void testConcurrentTransfersAndReads() throws InterruptedException {
        log.info("Starting test: Concurrent transfers and reads");

        int transferCount = 10;
        int readCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(transferCount + readCount);
        AtomicInteger successfulTransfers = new AtomicInteger(0);
        AtomicInteger successfulReads = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // Submit transfer tasks
        for (int i = 0; i < transferCount; i++) {
            final int transferId = i;
            executor.submit(() -> {
                try {
                    PostTransactionRequest transferRequest = new PostTransactionRequest();
                    transferRequest.setSourceAccountId(sourceAccountId);
                    transferRequest.setDestinationAccountId(destAccountId);
                    transferRequest.setAmount(new BigDecimal("10.00"));
                    transferRequest.setCurrency("INR");
                    transferRequest.setDescription("Concurrent transfer " + transferId);
                    transferRequest.setIdempotencyKey("concurrent-" + transferId + "-" + UUID.randomUUID());

                    transactionService.postTransfer(transferRequest);
                    successfulTransfers.incrementAndGet();
                    log.debug("Transfer {} completed", transferId);
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("Transfer {} failed: {}", transferId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Submit read tasks
        for (int i = 0; i < readCount; i++) {
            final int readId = i;
            executor.submit(() -> {
                try {
                    BigDecimal balance = accountService.getAccountBalance(sourceAccountId);
                    successfulReads.incrementAndGet();
                    log.debug("Read {} completed: balance={}", readId, balance);
                } catch (Exception e) {
                    errors.incrementAndGet();
                    log.warn("Read {} failed: {}", readId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;

        log.info("Concurrent operations completed:");
        log.info("  Successful transfers: {}", successfulTransfers.get());
        log.info("  Successful reads: {}", successfulReads.get());
        log.info("  Errors: {}", errors.get());
        log.info("  Total duration: {}ms", duration);

        assertTrue(errors.get() == 0, "No errors should occur during concurrent operations");
        assertEquals(transferCount, successfulTransfers.get(), "All transfers should complete successfully");
        assertEquals(readCount, successfulReads.get(), "All reads should complete successfully");

        // Verify final balance is correct
        BigDecimal finalBalance = accountService.getAccountBalance(sourceAccountId);
        BigDecimal dbBalance = journalEntryRepository.getAccountBalance(sourceAccountId);
        assertEquals(dbBalance, finalBalance, "Final cached balance must match database");
    }
}
