-- =============================================================================
-- Booking hour blocks
-- =============================================================================
-- Concrete one-hour availability units generated from patterns and exceptions.
-- Represents the atomic inventory unit for tutor search, temporary holds, and
-- final bookings. Includes optimistic locking (version) and partial indexes for
-- efficient availability lookups and automatic expiration of stale holds.
-- =============================================================================

CREATE TABLE IF NOT EXISTS booking.hour_blocks (
    id            uuid          PRIMARY KEY,
    tenant_id     varchar(32)   NOT NULL,
    tutor_id      uuid          NOT NULL,
    starts_at     timestamptz   NOT NULL,
    ends_at       timestamptz   NOT NULL,
    status        varchar(16)   NOT NULL,
    held_by       uuid,
    held_until    timestamptz,
    booking_id    uuid,
    pattern_id    uuid          REFERENCES booking.availability_patterns (id),
    version       bigint        NOT NULL DEFAULT 0,
    created_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_hour_blocks_tenant_tutor_start
        UNIQUE (tenant_id, tutor_id, starts_at),

    CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED', 'RELEASED')),
    CHECK ((status = 'BOOKED') = (booking_id IS NOT NULL)),
    CHECK ((status = 'HELD') = (held_until IS NOT NULL AND held_by IS NOT NULL)),
    CHECK (ends_at > starts_at)
);

-- Partial index for fast search of free blocks per tenant
CREATE INDEX IF NOT EXISTS idx_hour_blocks_tenant_available
    ON booking.hour_blocks (tenant_id, starts_at)
    WHERE status = 'AVAILABLE';

-- Partial index for background job releasing expired holds efficiently
CREATE INDEX IF NOT EXISTS idx_hour_blocks_held_until
    ON booking.hour_blocks (held_until)
    WHERE status = 'HELD';