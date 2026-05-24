-- =========================================================================
-- FLYWAY MIGRATION: V2__add_reconciliation_and_shedlock.sql
-- DESCRIPTION: Introduces distributed locking and batch audit tables
-- =========================================================================

-- 1. Create ShedLock synchronization layout table
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

-- 2. Create Reconciliation Mismatch Audit Ledger
CREATE TABLE reconciliation_mismatches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    provider_ref_id VARCHAR(255),
    internal_status VARCHAR(20) NOT NULL,
    external_status VARCHAR(20) NOT NULL,
    resolution_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_INVESTIGATION',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 3. High-Performance Indexing Boundaries
CREATE INDEX idx_recon_mismatches_txn ON reconciliation_mismatches(transaction_id);
CREATE INDEX idx_recon_mismatches_status ON reconciliation_mismatches(resolution_status);

-- 4. Critical performance optimize for Webhook Kafka consumer lookup from Phase 5
CREATE INDEX IF NOT EXISTS idx_transactions_provider_ref ON transactions(provider_ref_id);