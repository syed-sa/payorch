// File: reconciliation-worker/src/main/java/com/payorch/reconciliation/step/TransactionItemWriter.java
package com.payorch.reconciliation.step;

import com.payorch.reconciliation.domain.ReconciliationMismatch;
import com.payorch.reconciliation.repository.ReconciliationMismatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionItemWriter implements ItemWriter<ReconciliationMismatch> {

    private final ReconciliationMismatchRepository mismatchRepository;

    @Override
    public void write(Chunk<? extends ReconciliationMismatch> chunk) throws Exception {
        log.info("Batch writer flushing chunk of {} reconciliation mismatches to storage layers.", chunk.size());
        
        // 1. Commit all mismatches in a highly performant bulk batch save
        mismatchRepository.saveAll(chunk.getItems());

        // 2. Production Hook: Push metrics to Prometheus or Slack channels here
        for (ReconciliationMismatch mismatch : chunk.getItems()) {
            log.error("[RECONCILIATION AUDIT ALERT] Discrepancy Found! Transaction ID: {}. Local: {}, External: {}",
                    mismatch.getTransactionId(), mismatch.getInternalStatus(), mismatch.getExternalStatus());
        }
    }
}