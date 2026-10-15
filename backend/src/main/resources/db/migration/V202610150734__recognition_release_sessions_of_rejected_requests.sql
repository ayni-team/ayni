-- =============================================================================
-- Recognition: a rejected request gives its sessions back (US29)
-- =============================================================================
-- Until now a session could back one request and never another, rejected or not: UNIQUE
-- (tenant_id, session_id) kept it in the first request it was presented with. A student whose
-- request was rejected could therefore never present those hours again.
--
-- Rejecting a request now releases its sessions. The rows stay, so the rejected file is still what
-- the student presented, and released_at says when the session became free. The uniqueness of a
-- session holds only among the rows that were not released: a session backs one request at a time,
-- and may appear again in a later request once the earlier one was rejected. An approved request
-- never releases its sessions.
--
-- The sessions of the requests already rejected are released with the moment of their decision, as
-- if the rule had always been this one.
-- =============================================================================

ALTER TABLE recognition.request_sessions
  ADD COLUMN released_at timestamptz;

ALTER TABLE recognition.request_sessions
  DROP CONSTRAINT uq_request_sessions_session;

CREATE UNIQUE INDEX uq_request_sessions_session
  ON recognition.request_sessions (tenant_id, session_id)
  WHERE released_at IS NULL;

UPDATE recognition.request_sessions requested
   SET released_at = request.reviewed_at
  FROM recognition.requests request
 WHERE request.id = requested.request_id
   AND request.status = 'REJECTED';

COMMENT ON TABLE recognition.request_sessions IS
  'The sessions backing a request, as they were when it was submitted. A session backs one request at a time: the unique index on (tenant_id, session_id) covers the rows not released.';
COMMENT ON COLUMN recognition.request_sessions.released_at IS
  'When the request was rejected and the session became free to back another request. NULL while it backs this one.';
