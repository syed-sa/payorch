-- Enable UUID extension (PostgreSQL only)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================
-- ACCOUNTS
-- =========================
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0)
);

-- =========================
-- TRANSACTIONS
-- =========================
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_id VARCHAR(50),
    provider_ref_id VARCHAR(255),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transaction_status 
        CHECK (status IN ('INITIATED','PENDING','SUCCESS','FAILED'))
);

-- =========================
-- LEDGER ENTRIES (DOUBLE ENTRY)
-- =========================
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ledger_transaction 
        FOREIGN KEY (transaction_id) REFERENCES transactions(id),

    CONSTRAINT fk_ledger_account 
        FOREIGN KEY (account_id) REFERENCES accounts(id),

    CONSTRAINT chk_entry_type 
        CHECK (entry_type IN ('DEBIT','CREDIT'))
);

-- =========================
-- IDEMPOTENCY KEYS
-- =========================
CREATE TABLE idempotency_keys (
    id VARCHAR(255) PRIMARY KEY,
    request_hash TEXT,
    response_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================
-- PROVIDER CONFIGS
-- =========================
CREATE TABLE provider_configs (
    provider_id VARCHAR(50) PRIMARY KEY,
    api_key TEXT,
    api_secret TEXT,
    base_url TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================
-- PROVIDER HEALTH
-- =========================
CREATE TABLE provider_health (
    provider_id VARCHAR(50) PRIMARY KEY,
    is_active BOOLEAN DEFAULT TRUE,
    last_success_rate DECIMAL(5,2),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- =========================
-- OUTBOX (EVENT-DRIVEN)
-- =========================
CREATE TABLE outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================
-- INDEXES (PERFORMANCE)
-- =========================
CREATE INDEX idx_transactions_status 
    ON transactions(status);

CREATE INDEX idx_transactions_provider 
    ON transactions(provider_id);

CREATE INDEX idx_ledger_transaction 
    ON ledger_entries(transaction_id);

CREATE INDEX idx_ledger_account 
    ON ledger_entries(account_id);

CREATE INDEX idx_outbox_status 
    ON outbox(status);