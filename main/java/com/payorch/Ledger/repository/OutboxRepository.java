// Package: com.payorch.Ledger.repository
package com.payorch.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payorch.ledger.model.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(String status);
}