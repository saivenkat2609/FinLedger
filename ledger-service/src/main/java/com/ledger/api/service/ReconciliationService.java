package com.ledger.api.service;

import com.ledger.api.domain.Account;
import com.ledger.api.domain.Transaction;
import com.ledger.api.domain.TransactionStatusType;
import com.ledger.api.dto.DiscrepancyResponse;
import com.ledger.api.dto.ReconciliationReport;
import com.ledger.api.repository.AccountRepository;
import com.ledger.api.repository.JournalEntryRepository;
import com.ledger.api.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ReconciliationService {

    private final JournalEntryRepository journalEntryRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final CacheManager cacheManager;

    public ReconciliationService(JournalEntryRepository journalEntryRepository,
                               TransactionRepository transactionRepository,
                               AccountRepository accountRepository,
                               RedisTemplate<String, String> redisTemplate,
                               CacheManager cacheManager) {
        this.journalEntryRepository = journalEntryRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
    }

    public ReconciliationReport generateReconciliationReport(LocalDate fromDate, LocalDate toDate) {
        LocalDateTime startOfDay = fromDate.atStartOfDay();
        LocalDateTime endOfDay = toDate.atTime(LocalTime.MAX);

        log.info("Generating reconciliation report for period: {} to {}", fromDate, toDate);

        // Query total debits and credits for settled transactions in the period
        BigDecimal totalDebits = journalEntryRepository.sumDebitsByDateRange(startOfDay, endOfDay);
        BigDecimal totalCredits = journalEntryRepository.sumCreditsByDateRange(startOfDay, endOfDay);

        if (totalDebits == null) totalDebits = BigDecimal.ZERO;
        if (totalCredits == null) totalCredits = BigDecimal.ZERO;

        // Calculate difference
        BigDecimal difference = totalDebits.subtract(totalCredits);
        boolean isBalanced = difference.compareTo(BigDecimal.ZERO) == 0;

        // Count transactions in period
        Long transactionCount = transactionRepository.countBySettledAtBetween(startOfDay, endOfDay);

        log.info("Reconciliation report generated. Debits={}, Credits={}, Difference={}, Balanced={}, Transactions={}, " +
                "Period: {} to {}",
                totalDebits, totalCredits, difference, isBalanced, transactionCount, fromDate, toDate);

        if (!isBalanced) {
            log.warn("ALERT: Books are NOT balanced! Difference: {}, Period: {} to {}",
                    difference, fromDate, toDate);
        }

        return ReconciliationReport.builder()
                .balanced(isBalanced)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .differenceAmount(difference)
                .transactionCount(transactionCount != null ? transactionCount : 0L)
                .fromDate(fromDate)
                .toDate(toDate)
                .reportTimestamp(Instant.now().toString())
                .build();
    }


    public List<DiscrepancyResponse> findDiscrepancies(LocalDate fromDate, LocalDate toDate) {
        LocalDateTime startOfDay = fromDate.atStartOfDay();
        LocalDateTime endOfDay = toDate.atTime(LocalTime.MAX);

        log.info("Finding balance discrepancies for period: {} to {}", fromDate, toDate);

        List<DiscrepancyResponse> discrepancies = new ArrayList<>();

        // Get all accounts
        List<Account> allAccounts = accountRepository.findAll();

        for (Account account : allAccounts) {
            // Compute expected balance from journal entries
            BigDecimal expectedBalance = journalEntryRepository.getAccountBalance(account.getId());

            // Get cached balance from Redis
            String cacheKey = "balance:" + account.getId();
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);
            BigDecimal cachedBalance = cachedValue != null ? new BigDecimal(cachedValue) : null;

            // Check for discrepancies
            if (cachedBalance == null) {
                // Cache miss
                log.warn("Cache miss for account {}: expected balance is {}", account.getId(), expectedBalance);
                discrepancies.add(DiscrepancyResponse.builder()
                        .accountId(account.getId())
                        .accountName(account.getName())
                        .expectedBalance(expectedBalance)
                        .cachedBalance(null)
                        .differenceAmount(null)
                        .discrepancyType("MISSING_CACHE")
                        .build());
            } else if (expectedBalance.compareTo(cachedBalance) != 0) {
                // Balance mismatch
                BigDecimal difference = expectedBalance.subtract(cachedBalance);
                log.warn("Balance discrepancy for account {}: expected={}, cached={}, difference={}",
                        account.getId(), expectedBalance, cachedBalance, difference);
                discrepancies.add(DiscrepancyResponse.builder()
                        .accountId(account.getId())
                        .accountName(account.getName())
                        .expectedBalance(expectedBalance)
                        .cachedBalance(cachedBalance)
                        .differenceAmount(difference)
                        .discrepancyType("BALANCE_MISMATCH")
                        .build());
            }
        }

        log.info("Found {} account discrepancies", discrepancies.size());
        return discrepancies;
    }


    public void invalidateAccountCache(UUID accountId) {
        String cacheKey = "balance:" + accountId;
        redisTemplate.delete(cacheKey);
        log.debug("Invalidated balance cache for account: {}", accountId);
    }
}
