package com.payorch.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "settlement_records", indexes = {
        @Index(name = "idx_settlement_ref", columnList = "provider_ref_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider_ref_id", nullable = false, unique = true)
    private String providerRefId;

    @Column(name = "gross_amount", nullable = false)
    private BigDecimal grossAmount;

    @Column(name = "fee_amount", nullable = false)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @Column(name = "external_status")
    private String externalStatus;
}
