-- =============================================================================
-- Who confirmed the end of a session
-- =============================================================================
-- US11 closes a session when both participants confirm the end, or on its own
-- fifteen minutes after the booked hour when only one of them did, and asks
-- that who confirmed stays on record. end_confirmed_at is that record, one per
-- participant, null for whoever never confirmed.
--
-- A column of its own rather than left_at: left_at is when a participant left
-- the call, which is measured by the call, not declared by the participant.
-- =============================================================================

ALTER TABLE sessions.participations
    ADD COLUMN end_confirmed_at timestamptz;
