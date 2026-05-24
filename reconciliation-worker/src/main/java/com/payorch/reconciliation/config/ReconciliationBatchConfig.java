// File: reconciliation-worker/src/main/java/com/payorch/reconciliation/config/ReconciliationBatchConfig.java
package com.payorch.reconciliation.config;

import com.payorch.ledger.model.Transaction;
import com.payorch.reconciliation.domain.ReconciliationMismatch;
import com.payorch.reconciliation.step.TransactionItemProcessor;
import com.payorch.reconciliation.step.TransactionItemWriter;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class ReconciliationBatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionItemProcessor itemProcessor;
    private final TransactionItemWriter itemWriter;

    private static final int CHUNK_SIZE = 100;

    @Bean
    public JpaPagingItemReader<Transaction> reconciliationReader() {
        // Nightly lookback target constraint execution window parameter
        LocalDateTime lookbackThreshold = LocalDateTime.now().minusHours(24);

        return new JpaPagingItemReaderBuilder<Transaction>()
                .name("reconciliationTransactionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT t FROM Transaction t WHERE t.updatedAt >= :lookbackTime AND t.status = 'PENDING'")
                .parameterValues(Map.of("lookbackTime", lookbackThreshold))
                .pageSize(CHUNK_SIZE) // Stream records sequentially in pages to protect server memory boundaries
                .build();
    }

    @Bean
    public Step reconciliationStep() {
        return new StepBuilder("reconciliationStep", jobRepository)
                .<Transaction, ReconciliationMismatch>chunk(CHUNK_SIZE, transactionManager)
                .reader(reconciliationReader())
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