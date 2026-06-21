-- =========================================================================
-- FLYWAY MIGRATION: V6__remove_accounting_domain.sql
-- DESCRIPTION: Removes account/ledger storage and keeps transactions as
--              payment orchestration tracking records only.
-- =========================================================================

ALTER TABLE transactions
DROP CONSTRAINT IF EXISTS fk_transactions_sender_account,
DROP CONSTRAINT IF EXISTS fk_transactions_receiver_account;

DROP INDEX IF EXISTS idx_transactions_sender_account;
DROP INDEX IF EXISTS idx_transactions_receiver_account;
DROP INDEX IF EXISTS idx_ledger_transaction;
DROP INDEX IF EXISTS idx_ledger_account;

ALTER TABLE transactions
DROP COLUMN IF EXISTS sender_account_id,
DROP COLUMN IF EXISTS receiver_account_id,
ADD COLUMN IF NOT EXISTS merchant_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS customer_reference VARCHAR(255);

ALTER TABLE transactions
DROP CONSTRAINT IF EXISTS chk_transaction_status;

ALTER TABLE transactions
ADD CONSTRAINT chk_transaction_status
    CHECK (status IN ('INITIATED','PENDING','SUCCESS','FAILED','REFUNDED'));

CREATE INDEX IF NOT EXISTS idx_transactions_provider_ref
    ON transactions(provider_ref_id);

CREATE INDEX IF NOT EXISTS idx_transactions_merchant
    ON transactions(merchant_id);

DROP TABLE IF EXISTS ledger_entries;
DROP TABLE IF EXISTS accounts;
