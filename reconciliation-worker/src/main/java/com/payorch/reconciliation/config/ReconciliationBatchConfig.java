// File: reconciliation-worker/src/main/java/com/payorch/reconciliation/config/ReconciliationBatchConfig.java
package com.payorch.reconciliation.config;

import com.payorch.model.Transaction;
import com.payorch.reconciliation.domain.ReconciliationMismatch;
import com.payorch.reconciliation.step.TransactionItemProcessor;
import com.payorch.reconciliation.step.TransactionItemWriter;
import com.payorch.reconciliation.step.TransactionItemReader;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class ReconciliationBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final TransactionItemProcessor itemProcessor;
    private final TransactionItemWriter itemWriter;
    private final TransactionItemReader itemReader;

    private static final int CHUNK_SIZE = 100;

    @Bean
    public Step reconciliationStep() {
        return new StepBuilder("reconciliationStep", jobRepository)
                .<Transaction, ReconciliationMismatch>chunk(CHUNK_SIZE, transactionManager)
                .reader(itemReader)
                .processor(itemProcessor)
                .writer(itemWriter)
                .build();
    }

    @Bean
    public Job nightlyReconciliationJob() {
        return new JobBuilder("nightlyReconciliationJob", jobRepository)
                .start(reconciliationStep())
                .build();
    }
}