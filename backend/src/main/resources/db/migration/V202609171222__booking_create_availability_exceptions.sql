-- =============================================================================
-- Booking availability exceptions
-- =============================================================================
-- Overrides a tutor's recurring availability pattern for a specific calendar date.
-- A tutor can declare extra available time (ADD) outside their regular pattern,
-- or block out time (REMOVE) when they cannot take sessions, optionally bounded
-- by a specific time window.
-- =============================================================================

CREATE TABLE IF NOT EXISTS booking.availability_exceptions (
    id               uuid          PRIMARY KEY,
    tenant_id        varchar(32)   NOT NULL,
    tutor_id         uuid          NOT NULL,
    exception_date   date          NOT NULL,
    starts_at_time   time,
    ends_at_time     time,
    kind             varchar(8)    NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),

    CHECK (kind IN ('ADD', 'REMOVE')),
    CHECK (
        (starts_at_time IS NULL AND ends_at_time IS NULL) OR
        (starts_at_time IS NOT NULL AND ends_at_time IS NOT NULL AND ends_at_time > starts_at_time)
    )
    );

CREATE INDEX IF NOT EXISTS idx_availability_exceptions_tenant_tutor_date
    ON booking.availability_exceptions (tenant_id, tutor_id, exception_date);