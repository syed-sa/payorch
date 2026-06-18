package com.payorch.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Idempotency Key Store for ensuring exactly-once payment processing semantics.
 * Persists request/response pairs to enable safe replay of idempotent payment
 * requests.
 *
 * Enterprise Pattern: Write-through cache (Redis + Database)
 * - Redis: Fast lookup for cached responses (TTL: 24h default)
 * - Database: Durability + audit trail + recovery
 *
 * @see com.payorch.common.idempotency.IdempotencyManager
 */
@Entity
@Table(name = "idempotency_keys", indexes = {
        @Index(name = "idx_idempotency_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    /**
     * Client-provided idempotency key (typically UUID).
     * Uniqueness ensures exactly-once semantics.
     */
    @Id
    @Column(name = "id", length = 255)
    private String id;

    /**
     * Hash of the request body to detect if a replay is identical or a divergent
     * request.
     * If hashes differ → reject as potential fraud (client changed request
     * mid-retry).
     */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /**
     * Serialized ProviderResponse payload.
     * Returned as-is on replay to maintain idempotent semantics.
     */
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    /**
     * Tracks when this idempotency entry was recorded.
     * Used for TTL cleanup and audit trails.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Optional: Explicit expiry timestamp (enterprise pattern for PII/sensitive
     * data retention).
     * Null = use configured TTL default (24h).
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
