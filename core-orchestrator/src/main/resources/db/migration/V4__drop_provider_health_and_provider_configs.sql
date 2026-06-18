-- =========================================================================
-- FLYWAY MIGRATION: V4__drop_provider_health_and_provider_configs.sql
-- DESCRIPTION: Drops obsolete provider health and provider configs tables.
-- =========================================================================

-- Drop legacy provider configuration and health tracking tables now replaced by in-memory
-- Resilience4j circuit breakers and secure vault-based credential storage.

DROP TABLE IF EXISTS provider_health;
DROP TABLE IF EXISTS provider_configs;
