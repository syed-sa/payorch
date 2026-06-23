// File: reconciliation-worker/src/main/java/com/payorch/reconciliation/step/TransactionItemWriter.java
package com.payorch.reconciliation.step;

import com.payorch.reconciliation.domain.ReconciliationMismatch;
import com.payorch.reconciliation.repository.ReconciliationMismatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionItemWriter implements ItemWriter<List<ReconciliationMismatch>> {

    private final ReconciliationMismatchRepository mismatchRepository;

    @Override
    public void write(Chunk<? extends List<ReconciliationMismatch>> chunk) throws Exception {
        List<ReconciliationMismatch> mismatches = chunk.getItems().stream()
                .flatMap(List::stream)
                .toList();

        log.info("Batch writer flushing chunk of {} reconciliation mismatches to storage layers.", mismatches.size());
        
        // 1. Commit all mismatches in a highly performant bulk batch save
        mismatchRepository.saveAll(mismatches);

        // 2. Production Hook: Push metrics to Prometheus or Slack channels here
        for (ReconciliationMismatch mismatch : mismatches) {
            log.error("[RECONCILIATION AUDIT ALERT] Discrepancy Found! Type: {}. Transaction ID: {}. Local: {}, External: {}",
                    mismatch.getMismatchType(), mismatch.getTransactionId(), mismatch.getInternalStatus(),
                    mismatch.getExternalStatus());
        }
    }
}
