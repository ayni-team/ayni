-- =============================================================================
-- TS01 - Isolation between universities enforced by the database (ADR 0007)
-- =============================================================================
-- Until now only the application filtered by tenant_id, and a forgotten filter
-- read another university's rows. From here on PostgreSQL refuses it too:
--
--   * every table with tenant_id has row level security and one policy, which
--     compares tenant_id with app.tenant_id, the university the transaction
--     belongs to. The application sets it at the start of every transaction
--     with set_config(..., true), the function form of SET LOCAL, so it never
--     outlives the transaction on a pooled connection. Unset, or empty, it
--     matches nothing: a request without a university reads no rows;
--
--   * the application connects with its own role, ${app_user}, which owns no
--     table, is no superuser and has no BYPASSRLS, so the policies apply to it.
--     Flyway keeps running as the owner, which is not subject to them.
--
-- Four tables are exempt, as ADR 0007 says, and get no policy:
-- identity.tenants and identity.platform_admins belong to no university, and
-- identity.access_links and identity.user_sessions are read before the
-- university is known, to resolve the token that reveals it.
--
-- Two policies differ from the rest, because their tenant_id may be NULL:
-- skills.catalog_items, whose global items belong to every university, and
-- notifications.notifications, whose notices to a platform administrator
-- belong to none.
--
-- A table created after this migration needs its own ENABLE ROW LEVEL SECURITY
-- and policy. RowLevelSecurityPolicyTest fails the build when one is missing.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- The role of the application. Created here only when it does not exist yet,
-- so a database where an administrator already created it keeps theirs. Its
-- name and password are Flyway placeholders (spring.flyway.placeholders.*),
-- which default to the development values.
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_user}') THEN
        CREATE ROLE ${app_user} LOGIN PASSWORD '${app_password}'
            NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA identity TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA identity TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA identity TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA identity GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA skills TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA skills TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA skills TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA skills GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA skills GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA booking TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA booking TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA booking TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA booking GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA booking GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA matching TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA matching TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA matching TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA matching GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA matching GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA sessions TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA sessions TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA sessions TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA sessions GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA sessions GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA wallet TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA wallet TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA wallet TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA wallet GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA wallet GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA recognition TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA recognition TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA recognition TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA recognition GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA recognition GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA reputation TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA reputation TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA reputation TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA reputation GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA reputation GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA payments TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payments TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payments TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA payments GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA payments GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA audit TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA audit TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA audit TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA audit GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA analytics TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA analytics TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA analytics TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA analytics GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA analytics GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

GRANT USAGE ON SCHEMA notifications TO ${app_user};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA notifications TO ${app_user};
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA notifications TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA notifications GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA notifications GRANT USAGE, SELECT ON SEQUENCES TO ${app_user};

-- The two append only tables: the triggers already refuse a change, and the
-- role is not even allowed to ask.
REVOKE UPDATE, DELETE ON wallet.ledger_entries FROM ${app_user};
REVOKE UPDATE, DELETE ON audit.activity_log FROM ${app_user};


-- -----------------------------------------------------------------------------
-- One policy per table: the rows of the university of the transaction.
-- nullif turns the empty value of a transaction without a university into
-- NULL, which equals nothing.
-- -----------------------------------------------------------------------------

ALTER TABLE identity.users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON identity.users
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE identity.academic_records ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON identity.academic_records
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE identity.credit_policies ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON identity.credit_policies
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE skills.offered_skills ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON skills.offered_skills
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE skills.learning_interests ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON skills.learning_interests
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE skills.validation_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON skills.validation_requests
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE skills.evidence_files ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON skills.evidence_files
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE skills.skill_proposals ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON skills.skill_proposals
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE skills.taught_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON skills.taught_sessions
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE skills.academic_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON skills.academic_settings
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE booking.availability_patterns ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON booking.availability_patterns
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE booking.availability_exceptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON booking.availability_exceptions
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE booking.availability_pauses ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON booking.availability_pauses
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE booking.hour_blocks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON booking.hour_blocks
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE booking.bookings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON booking.bookings
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE booking.tutor_reliability ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON booking.tutor_reliability
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE matching.available_offers ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON matching.available_offers
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE sessions.sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sessions.sessions
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE sessions.participations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sessions.participations
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE sessions.presence_checks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sessions.presence_checks
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE wallet.credit_accounts ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON wallet.credit_accounts
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE wallet.credit_lots ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON wallet.credit_lots
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE wallet.ledger_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON wallet.ledger_entries
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE wallet.campus_benefits ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON wallet.campus_benefits
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE wallet.credit_uses ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON wallet.credit_uses
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE wallet.credit_use_confirmations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON wallet.credit_use_confirmations
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE recognition.rules ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recognition.rules
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE recognition.requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recognition.requests
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE recognition.request_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON recognition.request_sessions
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE reputation.tutor_standing ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON reputation.tutor_standing
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE reputation.ratings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON reputation.ratings
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE reputation.rating_windows ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON reputation.rating_windows
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE payments.purchases ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments.purchases
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE audit.activity_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit.activity_log
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE audit.anomalies ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit.anomalies
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE audit.anomaly_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit.anomaly_sessions
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE analytics.daily_indicators ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analytics.daily_indicators
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE analytics.counted_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analytics.counted_events
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

ALTER TABLE analytics.active_student_days ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analytics.active_student_days
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));


-- -----------------------------------------------------------------------------
-- A global item of the catalogue (tenant_id NULL) is every university's, and a
-- coordinator approving a proposal creates one (US43).
-- -----------------------------------------------------------------------------

ALTER TABLE skills.catalog_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON skills.catalog_items
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id IS NULL OR tenant_id = nullif(current_setting('app.tenant_id', true), ''));


-- -----------------------------------------------------------------------------
-- A notice with no university is a platform administrator's access link: it
-- is seen only from a transaction that has no university either.
-- -----------------------------------------------------------------------------

ALTER TABLE notifications.notifications ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notifications.notifications
    USING (tenant_id IS NOT DISTINCT FROM nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id IS NOT DISTINCT FROM nullif(current_setting('app.tenant_id', true), ''));
