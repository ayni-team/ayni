-- =============================================================================
-- Booking availability pauses
-- =============================================================================
-- Temporarily suspends a tutor's recurring availability over an extended date
-- range (e.g., vacations, exam periods, or temporary leaves). During this window,
-- no regular blocks are generated or made open for bookings.
-- =============================================================================

CREATE TABLE IF NOT EXISTS booking.availability_pauses (
    id          uuid          PRIMARY KEY,
    tenant_id   varchar(32)   NOT NULL,
    tutor_id    uuid          NOT NULL,
    starts_on   date          NOT NULL,
    ends_on     date          NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),

    CHECK (ends_on >= starts_on)
);
