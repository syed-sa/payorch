package com.payorch.common.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled cleanup for idempotency entries.
 * Removes expired entries daily to prevent unbounded table growth.
 *
 * Enterprise pattern: ShedLock ensures single execution across distributed cluster.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final IdempotencyManager idempotencyManager;

    /**
     * Daily cleanup of expired idempotency entries (runs at 2 AM UTC).
     * ShedLock ensures this runs on exactly one node in the cluster.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(
        name = "idempotencyCleanupTask",
        lockAtMostFor = "10m",
        lockAtLeastFor = "5m"
    )
    public void cleanupExpiredIdempotencyKeys() {
        try {
            log.info("Starting scheduled idempotency cleanup...");
            int deleted = idempotencyManager.cleanupExpiredEntries();
            log.info("Idempotency cleanup completed successfully: {} entries removed", deleted);
        } catch (Exception e) {
            log.error("Idempotency cleanup failed", e);
            // Do not re-throw: allow scheduler to continue
        }
    }
}
