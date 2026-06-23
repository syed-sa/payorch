package com.payorch.reconciliation.step;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.stereotype.Component;

import com.payorch.shared.model.Transaction;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
public class TransactionItemReader implements ItemStreamReader<Transaction> {

    private static final int PAGE_SIZE = 100;

    private final JpaPagingItemReader<Transaction> delegate;

    public TransactionItemReader(EntityManagerFactory entityManagerFactory) {
        LocalDateTime lookbackThreshold = LocalDateTime.now().minusHours(24);

        this.delegate = new JpaPagingItemReaderBuilder<Transaction>()
                .name("reconciliationTransactionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT t FROM Transaction t WHERE t.updatedAt >= :lookbackTime")
                .parameterValues(Map.of("lookbackTime", lookbackThreshold))
                .pageSize(PAGE_SIZE)
                .build();

        try {
            this.delegate.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TransactionItemReader delegate", e);
        }
    }

    @PostConstruct
    public void init() {
        log.debug("TransactionItemReader initialized");
    }

    @Override
    public Transaction read() throws Exception {
        return delegate.read();
    }

    @Override
    public void open(org.springframework.batch.item.ExecutionContext executionContext) throws ItemStreamException {
        delegate.open(executionContext);
    }

    @Override
    public void update(org.springframework.batch.item.ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        delegate.close();
    }
}
