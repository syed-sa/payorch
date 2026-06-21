-- Final PayOrch schema after applying all migrations through V6.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_id VARCHAR(50),
    provider_ref_id VARCHAR(255),
    merchant_id VARCHAR(255),
    customer_reference VARCHAR(255),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transaction_status
        CHECK (status IN ('INITIATED','PENDING','SUCCESS','FAILED','REFUNDED'))
);

CREATE TABLE idempotency_keys (
    id VARCHAR(255) PRIMARY KEY,
    request_hash TEXT,
    response_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP
);

CREATE TABLE outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE reconciliation_mismatches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    provider_ref_id VARCHAR(255),
    internal_status VARCHAR(20) NOT NULL,
    external_status VARCHAR(20) NOT NULL,
    resolution_status VARCHAR(50) NOT NULL DEFAULT 'PENDING_INVESTIGATION',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_provider ON transactions(provider_id);
CREATE INDEX idx_transactions_provider_ref ON transactions(provider_ref_id);
CREATE INDEX idx_transactions_merchant ON transactions(merchant_id);
CREATE INDEX idx_idempotency_created_at ON idempotency_keys(created_at);
CREATE INDEX idx_outbox_status ON outbox(status);
CREATE INDEX idx_outbox_event_type ON outbox(event_type);
CREATE INDEX idx_recon_mismatches_txn ON reconciliation_mismatches(transaction_id);
CREATE INDEX idx_recon_mismatches_status ON reconciliation_mismatches(resolution_status);
CREATE INDEX idx_recon_mismatches_created_at ON reconciliation_mismatches(created_at);
