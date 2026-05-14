package com.payorch.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payorch.ledger.model.Transaction;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {}