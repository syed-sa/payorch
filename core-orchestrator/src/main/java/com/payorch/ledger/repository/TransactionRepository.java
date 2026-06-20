package com.payorch.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.payorch.shared.model.Transaction;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    // Crucial for Webhook processing performance: Ensure provider_ref_id column has a database Index configured
    Optional<Transaction> findByProviderRefId(String providerRefId);
}