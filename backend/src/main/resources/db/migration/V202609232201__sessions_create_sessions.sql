-- =============================================================================
-- Sessions
-- =============================================================================
-- The live meeting a confirmed booking turns into. Sessions creates the row when
-- it hears BookingConfirmed; booking_id is a plain uuid because foreign keys do
-- not cross schemas, and UNIQUE because one booking is one session.
--
-- room_name is the identifier handed to the video service, unguessable so that a
-- stranger holding a link cannot walk in.
--
-- The other tables of this schema (participations, presence_checks, whiteboards,
-- support_materials) arrive with the stories that write them.
-- =============================================================================

CREATE TABLE IF NOT EXISTS sessions.sessions (
    id                  uuid          PRIMARY KEY,
    tenant_id           varchar(32)   NOT NULL,
    booking_id          uuid          NOT NULL,
    student_id          uuid          NOT NULL,
    tutor_id            uuid          NOT NULL,
    scheduled_start     timestamptz   NOT NULL,
    scheduled_end       timestamptz   NOT NULL,
    room_name           varchar(120)  NOT NULL,
    started_at          timestamptz,
    ended_at            timestamptz,
    status              varchar(24)   NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_sessions_booking UNIQUE (booking_id),
    CONSTRAINT uq_sessions_room_name UNIQUE (room_name),
    CONSTRAINT ck_sessions_status
        CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED',
                          'UNVERIFIED', 'ABANDONED', 'CANCELLED'))
);

-- Upcoming sessions of a university, which the presence check walks
CREATE INDEX IF NOT EXISTS idx_sessions_tenant_scheduled_start
    ON sessions.sessions (tenant_id, scheduled_start);
