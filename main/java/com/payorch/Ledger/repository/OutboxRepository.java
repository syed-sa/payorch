// Package: com.payorch.Ledger.repository
package com.payorch.Ledger.repository;

import com.payorch.Ledger.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(String status);
}