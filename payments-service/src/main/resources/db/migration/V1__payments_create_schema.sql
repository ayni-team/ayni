-- =============================================================================
-- ayni_payments: the database of the payments service (Database per Service, ADR 0011)
-- =============================================================================
-- The same schema name the module had in the monolith's database, so the tables, the queries and
-- the copy of the existing rows (payments-service/data-migration) read the same on both sides.
-- Nothing else of Ayni lives in this database, and no other service connects to it.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS payments;

COMMENT ON SCHEMA payments IS 'Credit purchases and the facts owed to the monolith';
