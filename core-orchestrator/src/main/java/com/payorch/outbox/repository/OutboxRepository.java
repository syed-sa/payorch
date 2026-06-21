package com.payorch.outbox.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;
import org.springframework.stereotype.Repository;

import com.payorch.outbox.model.OutboxEvent;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatus(String status);

    /**
     * SELECT ... FOR UPDATE SKIP LOCKED is the gold standard for high-performance
     * messaging queues built over relational DBs.
     * This prevents multiple instances of your service from picking up the exact
     * same outbox events.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({ @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2") }) // -2 translates to SKIP LOCKED
                                                                                         // in PostgreSQL/MySQL
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEventsForProcessing(Pageable pageable);
}