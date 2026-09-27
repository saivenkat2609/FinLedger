package com.ledger.api.repository;

import com.ledger.api.domain.Account;
import com.ledger.api.domain.EntryType;
import com.ledger.api.dto.AccountType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByNameAndCurrency(String name, String currency);
    Optional<Account> findByName(String name);
    List<Account> findByType(AccountType type);
    List<Account> findByIsActiveTrue();
    List<Account> findByCurrency(String currency);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM JournalEntry e " +
            "WHERE e.account.id = :accountId AND e.entryType = :type")
    BigDecimal sumByAccountAndType(@Param("accountId") UUID accountId, @Param("type") EntryType type);

    @Query(value = """
            SELECT COALESCE(SUM(CASE
                WHEN je.entry_type = 'CREDIT' THEN je.amount
                WHEN je.entry_type = 'DEBIT' THEN -je.amount
                ELSE 0
            END), 0)
            FROM journal_entries je
            WHERE je.account_id = :accountId
            """, nativeQuery = true)
    BigDecimal getAccountBalance(@Param("accountId") UUID accountId);

    // === Feature 11: Pagination & Filtering ===

    // List all accounts with pagination
    Page<Account> findAll(Pageable pageable);

    // Filter by type with pagination
    Page<Account> findByType(AccountType type, Pageable pageable);

    // Filter by currency with pagination
    Page<Account> findByCurrency(String currency, Pageable pageable);

    // Filter by active status with pagination
    Page<Account> findByIsActiveTrue(Pageable pageable);

    // Filter by type and active status with pagination
    Page<Account> findByTypeAndIsActiveTrue(AccountType type, Pageable pageable);

    // Filter by currency and active status with pagination
    Page<Account> findByCurrencyAndIsActiveTrue(String currency, Pageable pageable);

    // Search by name (contains) with pagination
    @Query("SELECT a FROM Account a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY a.name ASC")
    Page<Account> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Complex filter with pagination
    @Query("SELECT a FROM Account a WHERE " +
           "(:type IS NULL OR a.type = :type) AND " +
           "(:currency IS NULL OR a.currency = :currency) AND " +
           "(:onlyActive = false OR a.isActive = true) AND " +
           "(:searchTerm IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY a.name ASC")
    Page<Account> findWithFilters(
            @Param("type") AccountType type,
            @Param("currency") String currency,
            @Param("onlyActive") boolean onlyActive,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);
}
