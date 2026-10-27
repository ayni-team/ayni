-- =============================================================================
-- Resending a presence code
-- =============================================================================
-- US54-T4: a participant whose email did not arrive asks for the code again. The
-- new code replaces the old one in the same row (one code per participant and
-- per session stays true, uq_presence_checks_session_user), so what the row
-- needs to remember is how many times it was replaced and when the last time
-- was: the limit is a number of resends and a minimum time between two of them.
--
-- resend_count    how many times the code was replaced after its first issue
-- last_resent_at  when it was replaced the last time, null until the first resend
--
-- The attempts spent are kept when the code is replaced: a resend never gives
-- back the guesses of a code that was already being guessed.
-- =============================================================================

ALTER TABLE sessions.presence_checks
    ADD COLUMN resend_count   smallint    NOT NULL DEFAULT 0,
    ADD COLUMN last_resent_at timestamptz,
    ADD CONSTRAINT ck_presence_checks_resend_count
        CHECK (resend_count >= 0);
