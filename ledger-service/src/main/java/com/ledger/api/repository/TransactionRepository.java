package com.ledger.api.repository;

import com.ledger.api.domain.Transaction;
import com.ledger.api.domain.TransactionStatusType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Find transaction by idempotency key (for duplicate detection)
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    // Find transactions by status
    List<Transaction> findByStatus(TransactionStatusType status);

    // Find transactions within date range
    List<Transaction> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    // Count transactions by status
    long countByStatus(TransactionStatusType status);

    // Find pending transactions (for settlement)
    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING' ORDER BY t.createdAt ASC")
    List<Transaction> findPendingTransactions();

    // Find settled transactions for a date
    @Query("SELECT t FROM Transaction t WHERE t.status = com.ledger.api.domain.TransactionStatusType.SETTLED AND t.settledAt >= :startDate AND t.settledAt < :endDate")
    List<Transaction> findSettledTransactionsByDate(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Count settled transactions in a date range (for reconciliation report)
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status IN ('SETTLED', 'REVERSED') AND t.settledAt BETWEEN :startDate AND :endDate")
    long countBySettledAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
