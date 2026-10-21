-- =============================================================================
-- Student absences (US34)
-- =============================================================================
-- One row per session the student did not come to, so the tutor of a later
-- booking can be told how many times it happened. Only the count is ever
-- shown: not when, not with whom, not why.
--
-- One row per booking, never a counter, so each absence can be audited and
-- the same event delivered twice records it once. The student is whoever the
-- booking belonged to, kept here so the count is read by (tenant_id,
-- student_id) without joining bookings.
--
-- A tenant scoped table, so it gets its row level security policy here, as
-- every table with tenant_id does since TS01.
-- =============================================================================

CREATE TABLE booking.student_absences (
    id           uuid         PRIMARY KEY,
    tenant_id    varchar(32)  NOT NULL,
    student_id   uuid         NOT NULL,
    booking_id   uuid         NOT NULL REFERENCES booking.bookings(id),
    recorded_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_student_absences_booking
        UNIQUE (tenant_id, booking_id)
);

CREATE INDEX idx_student_absences_tenant_student
    ON booking.student_absences (tenant_id, student_id);

ALTER TABLE booking.student_absences ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON booking.student_absences
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));
