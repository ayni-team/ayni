-- =============================================================================
-- Skills: evidence submitted for a global tool, and the files that back it
-- =============================================================================
-- No academic record can vouch for a global tool, so the tutor presents evidence and a coordinator
-- of the same university approves or rejects it (US16). One request is one submission: a tutor
-- whose evidence was rejected submits again and gets a new request, so what was decided about the
-- first one stays readable.
--
-- The file itself lives in object storage; the database keeps the pointer.
--
-- Column for column as docs/database/data-model.md specifies them, checked against PostgreSQL 16.
-- The schema itself is created by V1. The two indexes on the foreign keys and the size check are
-- not in the model: they cost nothing and a request is always read through its skill and its files.
-- =============================================================================

CREATE TABLE skills.validation_requests (
  id                uuid          PRIMARY KEY,
  tenant_id         varchar(32)   NOT NULL,
  offered_skill_id  uuid          NOT NULL REFERENCES skills.offered_skills(id),
  student_note      varchar(1000),
  status            varchar(16)   NOT NULL,
  reviewed_by       uuid,
  reviewed_at       timestamptz,
  decision_reason   varchar(500),
  created_at        timestamptz   NOT NULL DEFAULT now(),

  CONSTRAINT ck_validation_requests_status CHECK (status IN ('SUBMITTED','APPROVED','REJECTED')),
  CONSTRAINT ck_validation_requests_reviewed
    CHECK (status = 'SUBMITTED' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

COMMENT ON COLUMN skills.validation_requests.reviewed_by IS
  'A coordinator of the same university. Never the student who submitted it.';

-- The coordinator's queue: only what still waits, and a resolved request never comes back to it.
CREATE INDEX ix_validation_requests_submitted
  ON skills.validation_requests (tenant_id, status)
  WHERE status = 'SUBMITTED';

CREATE INDEX ix_validation_requests_offered_skill
  ON skills.validation_requests (tenant_id, offered_skill_id, created_at DESC);

CREATE TABLE skills.evidence_files (
  id                     uuid          PRIMARY KEY,
  tenant_id              varchar(32)   NOT NULL,
  validation_request_id  uuid          NOT NULL REFERENCES skills.validation_requests(id),
  file_name              varchar(255)  NOT NULL,
  storage_key            varchar(512)  NOT NULL,
  content_type           varchar(100)  NOT NULL,
  size_bytes             bigint        NOT NULL,
  uploaded_at            timestamptz   NOT NULL DEFAULT now(),

  CONSTRAINT ck_evidence_files_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_evidence_files_request
  ON skills.evidence_files (tenant_id, validation_request_id);
