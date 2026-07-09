package com.payorch.reconciliation.repository;

import com.payorch.shared.model.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, UUID> {
    Optional<SettlementRecord> findByProviderRefId(String providerRefId);
}
