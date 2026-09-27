package com.ledger.api.repository;

import com.ledger.api.domain.Transaction;
import com.ledger.api.domain.TransactionStatusType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    // === Feature 11: Pagination & Filtering ===

    // List all transactions with pagination
    Page<Transaction> findAll(Pageable pageable);

    // Filter by status with pagination
    Page<Transaction> findByStatus(TransactionStatusType status, Pageable pageable);

    // Filter by status and date range with pagination
    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    Page<Transaction> findByStatusAndDateRange(
            @Param("status") TransactionStatusType status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    // Filter by date range with pagination
    Page<Transaction> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    // Filter by amount range with pagination
    @Query("SELECT t FROM Transaction t WHERE t.amount BETWEEN :minAmount AND :maxAmount ORDER BY t.createdAt DESC")
    Page<Transaction> findByAmountRange(
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);

    // Complex filter: status, date range, amount range, currency with pagination
    @Query("SELECT t FROM Transaction t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:startDate IS NULL OR t.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR t.createdAt <= :endDate) AND " +
           "(:minAmount IS NULL OR t.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR t.amount <= :maxAmount) AND " +
           "(:currency IS NULL OR t.currency = :currency) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findWithFilters(
            @Param("status") TransactionStatusType status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("currency") String currency,
            Pageable pageable);

    // Filter by currency with pagination
    Page<Transaction> findByCurrency(String currency, Pageable pageable);

    // Filter by currency and status with pagination
    Page<Transaction> findByCurrencyAndStatus(String currency, TransactionStatusType status, Pageable pageable);
}
