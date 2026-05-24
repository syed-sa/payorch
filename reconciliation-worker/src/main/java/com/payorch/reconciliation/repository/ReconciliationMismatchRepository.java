// File: reconciliation-worker/src/main/java/com/payorch/reconciliation/repository/ReconciliationMismatchRepository.java
package com.payorch.reconciliation.repository;

import com.payorch.reconciliation.domain.ReconciliationMismatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ReconciliationMismatchRepository extends JpaRepository<ReconciliationMismatch, UUID> {
}