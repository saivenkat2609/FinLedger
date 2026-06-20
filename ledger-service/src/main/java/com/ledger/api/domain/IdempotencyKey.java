package com.ledger.api.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys", indexes = {
    @Index(name = "idx_idempotency_keys_expires_at", columnList = "expires_at")
})
public class IdempotencyKey {
    @Id
    @Column(nullable = false, length = 255)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String responsePayload;

    @Column(nullable = false, name = "http_status_code")
    private Integer httpStatusCode;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public IdempotencyKey() {}

    public IdempotencyKey(String key, String responsePayload, Integer httpStatusCode, LocalDateTime expiresAt) {
        this.key = key;
        this.responsePayload = responsePayload;
        this.httpStatusCode = httpStatusCode;
        this.expiresAt = expiresAt;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public void setResponsePayload(String responsePayload) {
        this.responsePayload = responsePayload;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
