package com.payorch.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {"com.payorch.ledger.model", "com.payorch.reconciliation.domain"})
@EnableJpaRepositories(basePackages = {"com.payorch.ledger.repository", "com.payorch.reconciliation.repository"})
public class ReconciliationWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReconciliationWorkerApplication.class, args);
    }
}
