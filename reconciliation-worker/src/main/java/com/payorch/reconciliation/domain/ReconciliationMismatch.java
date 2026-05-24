// File: reconciliation-worker/src/main/java/com/payorch/reconciliation/domain/ReconciliationMismatch.java
package com.payorch.reconciliation.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_mismatches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationMismatch {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "provider_ref_id")
    private String providerRefId;

    @Column(name = "internal_status", nullable = false)
    private String internalStatus;

    @Column(name = "external_status", nullable = false)
    private String externalStatus;

    @Column(name = "resolution_status", nullable = false)
    private String resolutionStatus; // e.g., "PENDING_INVESTIGATION", "AUTO_RESOLVED"

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}