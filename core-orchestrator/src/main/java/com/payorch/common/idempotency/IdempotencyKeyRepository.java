package com.payorch.common.idempotency;

import com.payorch.shared.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Modifying
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt IS NOT NULL AND ik.expiresAt < :now")
    int deleteExpiredEntries(LocalDateTime now);

    @Modifying
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt IS NULL AND ik.createdAt < :cutoffTime")
    int deleteOlderThanTtl(LocalDateTime cutoffTime);
}
