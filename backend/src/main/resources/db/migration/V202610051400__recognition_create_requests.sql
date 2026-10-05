-- =============================================================================
-- Recognition: a student's request to their university, and the sessions that back it (US28)
-- =============================================================================
-- A student who reached the hours their university asks for submits a request. Ayni assembles the
-- file, the university decides, and Ayni certifies nothing by itself.
--
-- The figures of the request are copied when it is submitted: the total of hours, the number of
-- sessions and the average rating. The coordinator reads what the student presented, not a number
-- that moves while they review it. For the same reason each session keeps what it was when it was
-- presented: the skill taught, when it began and ended, and the rating the tutor had received.
--
-- What a request consumes are the sessions, not the credits. UNIQUE (tenant_id, session_id) is the
-- whole mechanism that keeps the same hours from being presented twice: a session appears in one
-- request and never in another. The credits stay untouched and spendable.
--
-- The schema itself is created by V1.
-- =============================================================================

CREATE TABLE recognition.requests (
  id               uuid           PRIMARY KEY,
  tenant_id        varchar(32)    NOT NULL,
  student_id       uuid           NOT NULL,
  total_hours      integer        NOT NULL,
  sessions_count   integer        NOT NULL,
  average_rating   numeric(3,2),
  status           varchar(16)    NOT NULL,
  reviewed_by      uuid,
  reviewed_at      timestamptz,
  decision_reason  varchar(1000),
  submitted_at     timestamptz    NOT NULL DEFAULT now(),

  CONSTRAINT ck_requests_status
    CHECK (status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED')),
  CONSTRAINT ck_requests_reviewed
    CHECK (status IN ('SUBMITTED','UNDER_REVIEW') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CONSTRAINT ck_requests_reason
    CHECK (status NOT IN ('APPROVED','REJECTED') OR (decision_reason IS NOT NULL AND length(btrim(decision_reason)) > 0)),
  CONSTRAINT ck_requests_totals
    CHECK (total_hours > 0 AND sessions_count > 0),
  CONSTRAINT ck_requests_average_rating
    CHECK (average_rating IS NULL OR (average_rating >= 0 AND average_rating <= 5))
);

-- The coordinator's queue: what is waiting in a university, oldest first.
CREATE INDEX ix_requests_tenant_status
  ON recognition.requests (tenant_id, status, submitted_at);

-- A student's own requests, newest first.
CREATE INDEX ix_requests_student
  ON recognition.requests (tenant_id, student_id, submitted_at DESC);

CREATE TABLE recognition.request_sessions (
  request_id       uuid          NOT NULL REFERENCES recognition.requests (id),
  session_id       uuid          NOT NULL,
  tenant_id        varchar(32)   NOT NULL,
  hours            smallint      NOT NULL,
  catalog_item_id  uuid          NOT NULL,
  started_at       timestamptz   NOT NULL,
  ended_at         timestamptz   NOT NULL,
  stars            smallint,

  PRIMARY KEY (request_id, session_id),
  CONSTRAINT uq_request_sessions_session UNIQUE (tenant_id, session_id),
  CONSTRAINT ck_request_sessions_hours CHECK (hours > 0),
  CONSTRAINT ck_request_sessions_stars CHECK (stars IS NULL OR stars BETWEEN 1 AND 5),
  CONSTRAINT ck_request_sessions_period CHECK (ended_at >= started_at)
);

COMMENT ON TABLE recognition.requests IS
  'A student asking their university to recognise the hours taught. The figures are copied at submission.';
COMMENT ON TABLE recognition.request_sessions IS
  'The sessions backing a request, as they were when it was submitted. UNIQUE (tenant_id, session_id) keeps a session from backing two.';
COMMENT ON COLUMN recognition.request_sessions.stars IS
  'The rating the tutor received for the session, when there was one at submission.';
