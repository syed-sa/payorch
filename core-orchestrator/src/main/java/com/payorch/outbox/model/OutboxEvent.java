package com.payorch.outbox.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    private String status; // PENDING, PROCESSED, FAILED

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}