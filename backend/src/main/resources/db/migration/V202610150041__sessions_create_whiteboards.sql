-- =============================================================================
-- The shared whiteboard of a session (US10, TS08)
-- =============================================================================
-- One board per session, as data-model.md describes it. content holds
-- {"version": n, "elements": [...]}: the elements drawn so far and the number
-- of operations applied, which is the order both participants apply them in
-- (ADR 0009). Every operation is persisted as it is applied, so nothing is left
-- to save when the session closes.
--
-- has_activity becomes true with the first operation and answers, without
-- reading the content, whether anything was drawn at all.
--
-- A tenant scoped table, so it gets its row level security policy here, as
-- every table with tenant_id does since TS01.
-- =============================================================================

CREATE TABLE sessions.whiteboards (
    id            uuid          NOT NULL,
    tenant_id     varchar(32)   NOT NULL,
    session_id    uuid          NOT NULL,
    content       jsonb         NOT NULL DEFAULT '{}',
    has_activity  boolean       NOT NULL DEFAULT false,
    updated_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_whiteboards
        PRIMARY KEY (id),
    CONSTRAINT uq_whiteboards_session
        UNIQUE (session_id),
    CONSTRAINT fk_whiteboards_session
        FOREIGN KEY (session_id) REFERENCES sessions.sessions (id)
);

ALTER TABLE sessions.whiteboards ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sessions.whiteboards
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));
