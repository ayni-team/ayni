-- =============================================================================
-- Session participations
-- =============================================================================
-- Who entered a session, as which of its two participants, and when. A row is
-- written the first time a participant joins; UNIQUE (session_id, user_id)
-- keeps it one row per person however many times they come back.
--
-- connected_seconds is what the real duration will be measured against, and
-- one of the signals the audit looks at. Nothing measures it yet: it arrives
-- with leaving and closing the session.
-- =============================================================================

CREATE TABLE sessions.participations (
    id                 uuid          NOT NULL,
    tenant_id          varchar(32)   NOT NULL,
    session_id         uuid          NOT NULL,
    user_id            uuid          NOT NULL,
    role               varchar(8)    NOT NULL,
    joined_at          timestamptz,
    left_at            timestamptz,
    connected_seconds  integer       NOT NULL DEFAULT 0,

    CONSTRAINT pk_participations
        PRIMARY KEY (id),
    CONSTRAINT fk_participations_session
        FOREIGN KEY (session_id) REFERENCES sessions.sessions (id),
    CONSTRAINT uq_participations_session_user
        UNIQUE (session_id, user_id),
    CONSTRAINT ck_participations_role
        CHECK (role IN ('STUDENT', 'TUTOR')),
    CONSTRAINT ck_participations_connected_seconds
        CHECK (connected_seconds >= 0)
);
