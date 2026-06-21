package com.ledger.api.repository;

import com.ledger.api.domain.EntryType;
import com.ledger.api.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    // Find all entries for a transaction
    List<JournalEntry> findByTransactionId(UUID transactionId);

    // Find all entries for an account
    List<JournalEntry> findByAccountId(UUID accountId);

    // Find entries by type (DEBIT or CREDIT)
    List<JournalEntry> findByEntryType(EntryType entryType);

    // Sum of all debits for an account
    @Query("SELECT COALESCE(SUM(je.amount), 0) FROM JournalEntry je " +
            "WHERE je.account.id = :accountId AND je.entryType = 'DEBIT'")
    BigDecimal sumDebitsByAccount(@Param("accountId") UUID accountId);

    // Sum of all credits for an account
    @Query("SELECT COALESCE(SUM(je.amount), 0) FROM JournalEntry je " +
            "WHERE je.account.id = :accountId AND je.entryType = 'CREDIT'")
    BigDecimal sumCreditsByAccount(@Param("accountId") UUID accountId);

    // Get balance (credits - debits)
    @Query("SELECT COALESCE(SUM(CASE " +
            "WHEN je.entryType = 'CREDIT' THEN je.amount " +
            "WHEN je.entryType = 'DEBIT' THEN -je.amount " +
            "ELSE 0 END), 0) " +
            "FROM JournalEntry je WHERE je.account.id = :accountId")
    BigDecimal getAccountBalance(@Param("accountId") UUID accountId);

    // Sum all debits for reconciliation (by date range)
    @Query("SELECT COALESCE(SUM(je.amount), 0) FROM JournalEntry je " +
            "WHERE je.entryType = 'DEBIT' " +
            "AND je.transaction.settledAt BETWEEN :startDate AND :endDate " +
            "AND je.transaction.status IN ('SETTLED', 'REVERSED')")
    BigDecimal sumDebitsByDateRange(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    // Sum all credits for reconciliation (by date range)
    @Query("SELECT COALESCE(SUM(je.amount), 0) FROM JournalEntry je " +
            "WHERE je.entryType = 'CREDIT' " +
            "AND je.transaction.settledAt BETWEEN :startDate AND :endDate " +
            "AND je.transaction.status IN ('SETTLED', 'REVERSED')")
    BigDecimal sumCreditsByDateRange(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);
}
