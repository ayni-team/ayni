-- recognition: recognition requests and the university decision
CREATE SCHEMA IF NOT EXISTS recognition;

CREATE TABLE recognition.rules (
  id              uuid          PRIMARY KEY,
  tenant_id       varchar(32)   NOT NULL,
  minimum_hours   integer       NOT NULL,
  minimum_rating  numeric(3,2),
  valid_from      date          NOT NULL,
  superseded_at   timestamptz,
  created_at      timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_rules_minimum_hours CHECK (minimum_hours > 0)
);

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
  CONSTRAINT ck_requests_status CHECK (status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED')),
  CONSTRAINT ck_requests_review CHECK (status IN ('SUBMITTED','UNDER_REVIEW') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);
CREATE INDEX ix_requests_status ON recognition.requests (tenant_id, status);
CREATE INDEX ix_requests_student ON recognition.requests (tenant_id, student_id, submitted_at DESC);

CREATE TABLE recognition.request_sessions (
  request_id  uuid         NOT NULL REFERENCES recognition.requests(id),
  session_id  uuid         NOT NULL,
  tenant_id   varchar(32)  NOT NULL,
  hours       smallint     NOT NULL,
  CONSTRAINT pk_request_sessions PRIMARY KEY (request_id, session_id),
  CONSTRAINT uq_request_sessions_session UNIQUE (tenant_id, session_id)
);
