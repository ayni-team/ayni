-- =============================================================================
-- Auditable tutor reliability incidents
-- =============================================================================
-- One row per incident, rather than a mutable counter, so late cancellations
-- can be audited and queried by tutor.

CREATE TABLE IF NOT EXISTS booking.tutor_reliability (
    id          uuid         PRIMARY KEY,
    tenant_id   varchar(32)  NOT NULL,
    tutor_id    uuid         NOT NULL,
    booking_id  uuid         NOT NULL REFERENCES booking.bookings(id),
    kind        varchar(24)  NOT NULL,
    occurred_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_tutor_reliability_kind
        CHECK (kind IN ('LATE_CANCELLATION', 'NO_SHOW')),
    CONSTRAINT uq_tutor_reliability_booking_kind
        UNIQUE (booking_id, kind)
);

CREATE INDEX IF NOT EXISTS idx_tutor_reliability_tenant_tutor
    ON booking.tutor_reliability (tenant_id, tutor_id);
