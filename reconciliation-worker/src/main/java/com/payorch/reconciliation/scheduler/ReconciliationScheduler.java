// File: reconciliation-worker/src/main/java/com/payorch/reconciliation/scheduler/ReconciliationScheduler.java
package com.payorch.reconciliation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final JobLauncher jobLauncher;
    private final Job nightlyReconciliationJob;

    /**
     * Triggers every night at 1:00 AM. 
     * Uses ShedLock to ensure absolute exclusivity across all deployed server instances.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @SchedulerLock(
        name = "ReconciliationScheduler_nightlyRun", 
        lockAtLeastFor = "PT5M", // Keep the lock for at least 5 minutes even if the job finishes early
        lockAtMostFor = "PT14M" // Force-release the lock if the node dies mid-execution
    )
    public void executeNightlyReconciliation() {
        log.info("Acquired distributed lock execution authorization token. Firing Reconciliation Batch Engine...");

        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addDate("runDate", new Date()) // Dynamic run parameter forces a new unique instance execution
                    .toJobParameters();

            jobLauncher.run(nightlyReconciliationJob, jobParameters);
            log.info("Nightly reconciliation job pipeline executed successfully.");

        } catch (Exception e) {
            log.error("Severe pipeline failure detected during execution of nightly reconciliation batch engine", e);
        }
    }
}