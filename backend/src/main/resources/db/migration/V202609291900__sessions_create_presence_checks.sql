-- =============================================================================
-- Presence checks
-- =============================================================================
-- The five minute code, the anti fraud mechanism of US54. Five minutes into a
-- session each participant gets a six digit code by email and types it into
-- the platform. Whoever is not really in the session does not have it.
--
-- Only the hash of the code is kept: the email is the only copy. The code
-- expires, and attempts stop at five, which ck_presence_checks_attempts also
-- enforces. UNIQUE (session_id, user_id) makes it one code per participant and
-- per session, so a sweep that runs twice issues nothing the second time.
-- =============================================================================

CREATE TABLE sessions.presence_checks (
    id            uuid          NOT NULL,
    tenant_id     varchar(32)   NOT NULL,
    session_id    uuid          NOT NULL,
    user_id       uuid          NOT NULL,
    code_hash     varchar(64)   NOT NULL,
    issued_at     timestamptz   NOT NULL DEFAULT now(),
    expires_at    timestamptz   NOT NULL,
    confirmed_at  timestamptz,
    attempts      smallint      NOT NULL DEFAULT 0,

    CONSTRAINT pk_presence_checks
        PRIMARY KEY (id),
    CONSTRAINT fk_presence_checks_session
        FOREIGN KEY (session_id) REFERENCES sessions.sessions (id),
    CONSTRAINT uq_presence_checks_session_user
        UNIQUE (session_id, user_id),
    CONSTRAINT ck_presence_checks_attempts
        CHECK (attempts BETWEEN 0 AND 5),
    CONSTRAINT ck_presence_checks_expiry
        CHECK (expires_at > issued_at)
);
