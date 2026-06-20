package com.payorch.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.payorch.shared.model.IdempotencyKey;

import java.time.LocalDateTime;

/**
 * Repository for idempotency key persistence.
 * Provides durable storage for request/response caching across service
 * restarts.
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    /**
     * Cleanup query: removes expired idempotency entries.
     * Enterprise pattern: scheduled batch cleanup to prevent unbounded table
     * growth.
     *
     * @param now current timestamp
     * @return count of deleted records
     */
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt IS NOT NULL AND ik.expiresAt < :now")
    int deleteExpiredEntries(LocalDateTime now);

    /**
     * Cleanup query: removes entries older than TTL (default 24h).
     * Fallback when explicit expiry is not set.
     *
     * @param cutoffTime timestamp before which entries are considered expired
     * @return count of deleted records
     */
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt IS NULL AND ik.createdAt < :cutoffTime")
    int deleteOlderThanTtl(LocalDateTime cutoffTime);
}
