-- =============================================================================
-- Transactional Outbox (ADR 0011)
-- =============================================================================
-- In the monolith a confirmed purchase was a PurchaseConfirmed event that the
-- wallet heard in the same process, stored by the event publication registry
-- in the same transaction (TS04). Across a network that guarantee needs a table
-- of its own: the confirmation and its row here are written in one transaction,
-- and OutboxRelay delivers the row to the monolith afterwards, retrying with a
-- growing wait until the monolith accepts it (202). It is delivered at least
-- once; the wallet credits a purchase once however many times it arrives.
--
-- One row per event and purchase: the unique key makes recording the same fact
-- twice a no-op. A delivered row keeps sent_at as the record of when.
--
-- The rows belong to a university, so they get the same policy as purchases,
-- and payments.tenants_with_unsent_events() tells the relay which universities
-- to bind, the same way the reconciliation job learns its own.
-- =============================================================================

CREATE TABLE payments.outbox (
  id               uuid          PRIMARY KEY,
  tenant_id        varchar(32)   NOT NULL,
  event_type       varchar(64)   NOT NULL,
  aggregate_id     uuid          NOT NULL,
  payload          jsonb         NOT NULL,
  created_at       timestamptz   NOT NULL,
  attempts         integer       NOT NULL DEFAULT 0,
  next_attempt_at  timestamptz   NOT NULL,
  sent_at          timestamptz,
  last_error       varchar(500),
  CONSTRAINT uq_outbox_event_aggregate UNIQUE (event_type, aggregate_id),
  CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_outbox_unsent
  ON payments.outbox (tenant_id, next_attempt_at)
  WHERE sent_at IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON payments.outbox TO ${app_user};

ALTER TABLE payments.outbox ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments.outbox
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

CREATE FUNCTION payments.tenants_with_unsent_events(due_at timestamptz)
    RETURNS TABLE (tenant_id varchar)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
AS $$
    SELECT DISTINCT o.tenant_id
    FROM payments.outbox o
    WHERE o.sent_at IS NULL AND o.next_attempt_at <= due_at
    ORDER BY o.tenant_id
$$;

REVOKE ALL ON FUNCTION payments.tenants_with_unsent_events(timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION payments.tenants_with_unsent_events(timestamptz) TO ${app_user};
