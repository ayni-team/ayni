-- skills: catalogue of courses and tools, offered skills and accreditations
CREATE SCHEMA IF NOT EXISTS skills;

CREATE TABLE skills.categories (
  id          uuid         PRIMARY KEY,
  name        varchar(80)  NOT NULL UNIQUE,
  sort_order  smallint     NOT NULL DEFAULT 0
);

CREATE TABLE skills.catalog_items (
  id           uuid          PRIMARY KEY,
  scope        varchar(16)   NOT NULL,
  tenant_id    varchar(32),
  category_id  uuid          NOT NULL REFERENCES skills.categories(id),
  name         varchar(160)  NOT NULL,
  description  varchar(500),
  course_code  varchar(32),
  status       varchar(16)   NOT NULL,
  created_at   timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT ck_catalog_items_scope CHECK (scope IN ('GLOBAL','UNIVERSITY')),
  CONSTRAINT ck_catalog_items_scope_tenant CHECK ((scope = 'GLOBAL') = (tenant_id IS NULL)),
  CONSTRAINT ck_catalog_items_course_code CHECK (scope = 'GLOBAL' OR course_code IS NOT NULL),
  CONSTRAINT ck_catalog_items_status CHECK (status IN ('ACTIVE','RETIRED')),
  CONSTRAINT uq_catalog_items_course UNIQUE (tenant_id, course_code)
);
CREATE INDEX ix_catalog_items_tenant_status ON skills.catalog_items (tenant_id, status);

CREATE TABLE skills.offered_skills (
  id                  uuid          PRIMARY KEY,
  tenant_id           varchar(32)   NOT NULL,
  tutor_id            uuid          NOT NULL,
  catalog_item_id     uuid          NOT NULL REFERENCES skills.catalog_items(id),
  status              varchar(16)   NOT NULL,
  accreditation_path  varchar(24),
  accredited_grade    numeric(4,2),
  enabled_at          timestamptz,
  created_at          timestamptz   NOT NULL DEFAULT now(),
  updated_at          timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_offered_skills UNIQUE (tenant_id, tutor_id, catalog_item_id),
  CONSTRAINT ck_offered_skills_status CHECK (status IN ('PENDING','ENABLED','REJECTED','WITHDRAWN')),
  CONSTRAINT ck_offered_skills_path CHECK (accreditation_path IN ('ACADEMIC_RECORD','REVIEWED_EVIDENCE')),
  CONSTRAINT ck_offered_skills_enabled_path CHECK (status <> 'ENABLED' OR accreditation_path IS NOT NULL)
);
CREATE INDEX ix_offered_skills_enabled ON skills.offered_skills (tenant_id, catalog_item_id) WHERE status = 'ENABLED';

CREATE TABLE skills.validation_requests (
  id                uuid           PRIMARY KEY,
  tenant_id         varchar(32)    NOT NULL,
  offered_skill_id  uuid           NOT NULL REFERENCES skills.offered_skills(id),
  student_note      varchar(1000),
  status            varchar(16)    NOT NULL,
  reviewed_by       uuid,
  reviewed_at       timestamptz,
  decision_reason   varchar(500),
  created_at        timestamptz    NOT NULL DEFAULT now(),
  CONSTRAINT ck_validation_requests_status CHECK (status IN ('SUBMITTED','APPROVED','REJECTED')),
  CONSTRAINT ck_validation_requests_review CHECK (status = 'SUBMITTED' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);
CREATE INDEX ix_validation_requests_pending ON skills.validation_requests (tenant_id, status) WHERE status = 'SUBMITTED';

CREATE TABLE skills.evidence_files (
  id                     uuid          PRIMARY KEY,
  tenant_id              varchar(32)   NOT NULL,
  validation_request_id  uuid          NOT NULL REFERENCES skills.validation_requests(id),
  file_name              varchar(255)  NOT NULL,
  storage_key            varchar(512)  NOT NULL,
  content_type           varchar(100)  NOT NULL,
  size_bytes             bigint        NOT NULL,
  uploaded_at            timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE skills.learning_interests (
  id               uuid          PRIMARY KEY,
  tenant_id        varchar(32)   NOT NULL,
  student_id       uuid          NOT NULL,
  catalog_item_id  uuid          NOT NULL REFERENCES skills.catalog_items(id),
  created_at       timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT uq_learning_interests UNIQUE (tenant_id, student_id, catalog_item_id)
);
