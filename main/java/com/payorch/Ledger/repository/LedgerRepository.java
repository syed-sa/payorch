package com.payorch.Ledger.repository;

import com.payorch.Ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {}