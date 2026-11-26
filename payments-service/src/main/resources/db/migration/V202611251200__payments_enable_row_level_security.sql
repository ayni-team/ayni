-- =============================================================================
-- TS01 in the database of the payments service (ADR 0007, ADR 0011)
-- =============================================================================
-- The isolation between universities the monolith gives every table, kept here
-- the same way:
--
--   * payments.purchases has row level security and one policy, which compares
--     tenant_id with app.tenant_id, the university every transaction sets with
--     set_config(..., true) (TenantTransactionManager). Unset, or empty, it
--     matches nothing;
--
--   * the service connects with its own role, ${app_user}, which owns no table,
--     is no superuser and has no BYPASSRLS, so the policy applies to it. Flyway
--     runs as the owner of the database, which is not subject to it.
--
-- The reconciliation job must find the universities that have pending
-- purchases before it can bind any of them. In the monolith it asked identity
-- for the active universities; here there is no identity, and asking the table
-- with no university bound answers nothing. payments.tenants_with_pending_purchases()
-- is the one way around the policy: a function of the owner (SECURITY DEFINER)
-- that answers the distinct codes and nothing else, no purchase, no student, no
-- amount. The job then binds each code and reads its purchases under the policy.
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_user}') THEN
        CREATE ROLE ${app_user} LOGIN PASSWORD '${app_password}'
            NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA payments TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payments TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payments TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA payments GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA payments GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

ALTER TABLE payments.purchases ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments.purchases
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

CREATE FUNCTION payments.tenants_with_pending_purchases()
    RETURNS TABLE (tenant_id varchar)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
AS $$
    SELECT DISTINCT p.tenant_id
    FROM payments.purchases p
    WHERE p.status = 'PENDING'
    ORDER BY p.tenant_id
$$;

REVOKE ALL ON FUNCTION payments.tenants_with_pending_purchases() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION payments.tenants_with_pending_purchases() TO ${app_user};
