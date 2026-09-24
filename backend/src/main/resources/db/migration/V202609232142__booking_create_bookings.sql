-- =============================================================================
-- Booking reservations
-- =============================================================================
-- A booking covers one or more consecutive hours with the same tutor. It is
-- written by the confirmation, in the same transaction that charges the credits
-- and marks the hour blocks as BOOKED, so a row here always means both happened.
--
-- credits_charged = hours is the pricing rule written as a constraint: one
-- credit, one hour.
--
-- booking.tutor_reliability, the other table the data model places in this
-- schema, is created by the story that records late cancellations: nothing
-- writes to it yet.
-- =============================================================================

CREATE TABLE IF NOT EXISTS booking.bookings (
    id                  uuid          PRIMARY KEY,
    tenant_id           varchar(32)   NOT NULL,
    student_id          uuid          NOT NULL,
    tutor_id            uuid          NOT NULL,
    catalog_item_id     uuid          NOT NULL,
    starts_at           timestamptz   NOT NULL,
    ends_at             timestamptz   NOT NULL,
    hours               smallint      NOT NULL,
    credits_charged     integer       NOT NULL,
    need_description    text          NOT NULL,
    status              varchar(16)   NOT NULL,
    cancelled_by        varchar(16),
    cancelled_at        timestamptz,
    cancelled_late      boolean,
    cancellation_reason varchar(500),
    created_at          timestamptz   NOT NULL DEFAULT now(),
    updated_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_bookings_status
        CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT ck_bookings_pricing
        CHECK (hours >= 1 AND credits_charged = hours),
    CONSTRAINT ck_bookings_range
        CHECK (ends_at > starts_at),
    CONSTRAINT ck_bookings_cancellation
        CHECK (status <> 'CANCELLED' OR (cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL)),
    -- Enumerations are varchar with a CHECK, as everywhere else in the model.
    CONSTRAINT ck_bookings_cancelled_by
        CHECK (cancelled_by IS NULL OR cancelled_by IN ('STUDENT', 'TUTOR', 'SYSTEM'))
);

-- A student's upcoming bookings and history, newest first
CREATE INDEX IF NOT EXISTS idx_bookings_tenant_student_start
    ON booking.bookings (tenant_id, student_id, starts_at DESC);

-- A tutor's agenda, newest first
CREATE INDEX IF NOT EXISTS idx_bookings_tenant_tutor_start
    ON booking.bookings (tenant_id, tutor_id, starts_at DESC);
