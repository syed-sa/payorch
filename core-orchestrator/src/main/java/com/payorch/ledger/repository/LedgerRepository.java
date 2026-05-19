package com.payorch.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payorch.ledger.model.LedgerEntry;

import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {}