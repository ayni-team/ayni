-- =============================================================================
-- Support material of a session (US07)
-- =============================================================================
-- Prepared from the need the student wrote when booking, once the session
-- exists, so the tutor arrives prepared. The table is the one of data-model.md
-- plus UNIQUE (session_id): a session has one material, and the same
-- confirmation delivered twice prepares it once.
--
-- No row means there is no material: the service that prepares it failed or
-- has not answered, and the session shows only the need.
-- =============================================================================

CREATE TABLE sessions.support_materials (
    id            uuid          NOT NULL,
    tenant_id     varchar(32)   NOT NULL,
    session_id    uuid          NOT NULL,
    summary       text          NOT NULL,
    suggestions   jsonb,
    generated_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_support_materials
        PRIMARY KEY (id),
    CONSTRAINT uq_support_materials_session
        UNIQUE (session_id),
    CONSTRAINT fk_support_materials_session
        FOREIGN KEY (session_id) REFERENCES sessions.sessions (id)
);

ALTER TABLE sessions.support_materials ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sessions.support_materials
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));
