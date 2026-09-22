-- =============================================================================
-- Matching available offers
-- =============================================================================
-- Read projection that makes tutor search fast. Each row is a concrete one-hour
-- block that a tutor has open for a given course, fed by events published from
-- booking (HoursGenerated adds a row, HoursWithdrawn removes it) and skills
-- (approved courses and learning interests). The search use case reads only
-- from this table; it never queries booking or skills directly.
--
-- Reservation state (locking, booking, cancellation) is owned by booking and
-- wallet, not duplicated here: when a block stops being available for any
-- reason, booking publishes HoursWithdrawn and this row is removed.
-- =============================================================================

CREATE TABLE IF NOT EXISTS matching.available_offers (
                                                         id                    uuid          PRIMARY KEY,
                                                         tenant_id             varchar(32)   NOT NULL,
    tutor_id              uuid          NOT NULL,
    course_id             uuid          NOT NULL,
    source_hour_block_id  uuid          NOT NULL,
    starts_at             timestamptz   NOT NULL,
    ends_at               timestamptz   NOT NULL,
    tutor_rating          numeric(3,2),
    is_new_tutor          boolean       NOT NULL DEFAULT false,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),

    CHECK (ends_at > starts_at)
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_available_offers_source_hour_block
    ON matching.available_offers (source_hour_block_id);

CREATE INDEX IF NOT EXISTS idx_available_offers_tenant_course_starts
    ON matching.available_offers (tenant_id, course_id, starts_at);

CREATE INDEX IF NOT EXISTS idx_available_offers_tenant_tutor
    ON matching.available_offers (tenant_id, tutor_id);



