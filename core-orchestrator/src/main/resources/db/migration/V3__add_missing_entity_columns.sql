-- =========================================================================
-- FLYWAY MIGRATION: V3__add_missing_entity_columns.sql
-- DESCRIPTION: Adds missing columns and constraints for transaction relationships
--              and account audit trail to align with JPA entity mappings
-- =========================================================================

-- 1. Add sender and receiver account relationships to transactions
ALTER TABLE transactions
ADD COLUMN IF NOT EXISTS sender_account_id UUID,
ADD COLUMN IF NOT EXISTS receiver_account_id UUID;

-- Add foreign key constraints
ALTER TABLE transactions
ADD CONSTRAINT fk_transactions_sender_account 
    FOREIGN KEY (sender_account_id) REFERENCES accounts(id);

ALTER TABLE transactions
ADD CONSTRAINT fk_transactions_receiver_account 
    FOREIGN KEY (receiver_account_id) REFERENCES accounts(id);

-- 2. Add created_at timestamp to accounts for audit trail
-- Use a safe approach: add column as nullable first, then backfill, then set NOT NULL if needed
ALTER TABLE accounts
ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW();

-- 3. Create indexes for transaction account lookups (performance optimization)
CREATE INDEX IF NOT EXISTS idx_transactions_sender_account 
    ON transactions(sender_account_id);

CREATE INDEX IF NOT EXISTS idx_transactions_receiver_account 
    ON transactions(receiver_account_id);

-- 4. Add indexes for outbox status and event_type lookup performance
CREATE INDEX IF NOT EXISTS idx_outbox_event_type 
    ON outbox(event_type);

-- 5. Add index on reconciliation_mismatches created_at for time-range queries
CREATE INDEX IF NOT EXISTS idx_recon_mismatches_created_at 
    ON reconciliation_mismatches(created_at);
