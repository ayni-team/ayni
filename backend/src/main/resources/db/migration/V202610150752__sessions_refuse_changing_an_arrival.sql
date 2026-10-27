-- =============================================================================
-- Sessions: the database refuses to change an arrival time (US09)
-- =============================================================================
-- joined_at is a participant's first arrival and is never moved: coming back after a dropped
-- connection does not change it, and both participants read it as the record of who came when.
-- The application writes it once, when the participant first joins. Until now nothing stopped an
-- UPDATE made straight on the table from rewriting it.
--
-- A BEFORE UPDATE trigger now refuses any update that changes a joined_at that was already set.
-- Recording the first arrival, from NULL to a moment, is still allowed, and so is changing any
-- other column of the row (end_confirmed_at, left_at, connected_seconds), or writing joined_at
-- again with the value it already has, which is what an ORM does when it saves the whole row.
--
-- Like the triggers of audit.activity_log, it stops accidents and the application, not whoever
-- owns the table, who can drop it.
-- =============================================================================

CREATE FUNCTION sessions.reject_arrival_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'sessions.participations.joined_at is recorded once and cannot be changed'
    USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER tr_participations_arrival_immutable
  BEFORE UPDATE OF joined_at ON sessions.participations
  FOR EACH ROW
  WHEN (OLD.joined_at IS NOT NULL AND NEW.joined_at IS DISTINCT FROM OLD.joined_at)
  EXECUTE FUNCTION sessions.reject_arrival_change();

COMMENT ON COLUMN sessions.participations.joined_at IS
  'The first arrival of the participant. Set once; tr_participations_arrival_immutable refuses to change it.';
