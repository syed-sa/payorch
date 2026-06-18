-- =========================================================================
-- FLYWAY MIGRATION: V5__enhance_idempotency_with_database_persistence.sql
-- DESCRIPTION: Upgrades idempotency_keys table with request hash validation
--              and TTL management for enterprise write-through caching pattern.
-- =========================================================================

-- 1. Add request_hash column for divergent replay detection
ALTER TABLE idempotency_keys
ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64) DEFAULT '';

-- 2. Add expires_at column for TTL-based cleanup
ALTER TABLE idempotency_keys
ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP DEFAULT NULL;

-- 3. Add composite index for efficient cleanup queries (TTL-based)
CREATE INDEX IF NOT EXISTS idx_idempotency_keys_created_at 
    ON idempotency_keys(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at 
    ON idempotency_keys(expires_at);

-- 4. Add JSONB support for response_payload (optional: for structured queries)
-- PostgreSQL: response_payload is already TEXT, can be cast to JSONB in queries
-- For native JSONB support, uncomment the following:
-- ALTER TABLE idempotency_keys
-- ALTER COLUMN response_payload TYPE JSONB USING response_payload::JSONB;

-- Comment: idempotency_keys table now supports:
-- - Write-through caching (Redis L1 + Database L2)
-- - Request hash validation (divergent replay prevention)
-- - Automatic TTL cleanup (scheduled job at 2 AM UTC via ShedLock)
-- - Audit trail for compliance (immutable created_at)
