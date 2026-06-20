package com.ledger.api.repository;

import com.ledger.api.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByKey(String key);

    @Modifying
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt < :expiresAt")
    int deleteExpiredBefore(@Param("expiresAt") LocalDateTime expiresAt);

    // INSERT ... ON CONFLICT DO NOTHING — atomic race condition guard
    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (key, response_payload, http_status_code, created_at, expires_at) " +
                   "VALUES (:key, :responsePayload, :httpStatusCode, NOW(), :expiresAt) " +
                   "ON CONFLICT (key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("key") String key,
                       @Param("responsePayload") String responsePayload,
                       @Param("httpStatusCode") Integer httpStatusCode,
                       @Param("expiresAt") LocalDateTime expiresAt);
}
