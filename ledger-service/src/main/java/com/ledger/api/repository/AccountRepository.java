package com.ledger.api.repository;

import com.ledger.api.domain.Account;
import com.ledger.api.domain.EntryType;
import com.ledger.api.dto.AccountType;
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
}
