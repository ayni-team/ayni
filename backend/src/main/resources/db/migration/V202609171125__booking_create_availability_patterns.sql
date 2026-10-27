-- =============================================================================
-- Booking availability patterns
-- =============================================================================
-- A tutor can declare recurrent availability by weekday and time window. Each row
-- is tenant-scoped and valid for a date range, which is the input used to
-- generate the concrete one-hour blocks later.
-- =============================================================================

CREATE TABLE IF NOT EXISTS booking.availability_patterns (
    id               uuid          PRIMARY KEY,
    tenant_id        varchar(32)   NOT NULL,
    tutor_id         uuid          NOT NULL,
    day_of_week      smallint      NOT NULL,
    starts_at_time   time          NOT NULL,
    ends_at_time     time          NOT NULL,
    valid_from       date          NOT NULL,
    valid_until      date,
    created_at       timestamptz   NOT NULL DEFAULT now(),

    CHECK (day_of_week BETWEEN 1 AND 7),
    CHECK (ends_at_time > starts_at_time)
);

CREATE INDEX IF NOT EXISTS idx_availability_patterns_tenant_tutor
    ON booking.availability_patterns (tenant_id, tutor_id);
