-- =============================================================================
-- Audit: the activity log
-- =============================================================================
-- One row per thing that happened to credits, bookings, sessions, skills and
-- recognition, and one per refused attempt to read the log. It is what a
-- coordinator answers a challenged recognition with, so it is append only.
--
-- actor_id is nullable because some events name nobody: the system closes a
-- session, and a validation or a recognition decision does not say which
-- coordinator took it. actor_role says who acted in every case: STUDENT, TUTOR,
-- COORDINATOR, SYSTEM, or UNKNOWN for somebody the university does not know.
--
-- metadata never holds an email address, a link or a code. Those travel in
-- events that audit does not listen to.
-- =============================================================================

CREATE TABLE audit.activity_log (
    id             uuid          NOT NULL,
    tenant_id      varchar(32)   NOT NULL,
    actor_id       uuid,
    actor_role     varchar(16)   NOT NULL,
    action         varchar(64)   NOT NULL,
    resource_type  varchar(32)   NOT NULL,
    resource_id    uuid,
    metadata       jsonb,
    occurred_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_activity_log
        PRIMARY KEY (id)
);

CREATE INDEX ix_activity_log_tenant_time
    ON audit.activity_log (tenant_id, occurred_at DESC);

CREATE INDEX ix_activity_log_resource
    ON audit.activity_log (tenant_id, resource_type, resource_id);

-- -----------------------------------------------------------------------------
-- Append only, in the database itself.
--
-- The same mechanism as wallet.ledger_entries: a trigger refuses UPDATE and
-- DELETE, so the guarantee does not depend on the application behaving, and
-- somebody with a psql prompt is refused too. TRUNCATE skips row triggers, so it
-- has a statement trigger of its own. A correction is a new row.
-- -----------------------------------------------------------------------------
CREATE FUNCTION audit.reject_activity_log_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'audit.activity_log is append only';
END;
$$;

CREATE TRIGGER tr_activity_log_append_only
  BEFORE UPDATE OR DELETE ON audit.activity_log
  FOR EACH ROW EXECUTE FUNCTION audit.reject_activity_log_change();

CREATE TRIGGER tr_activity_log_no_truncate
  BEFORE TRUNCATE ON audit.activity_log
  FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_activity_log_change();
