-- =============================================================================
-- Skills: what a tutor is enabled to teach
-- =============================================================================
-- A university course reaches ENABLED on its own when the grade clears the
-- threshold (US13). A global tool can only reach it through reviewed evidence,
-- because no academic record can vouch for it.
--
-- accredited_grade is copied rather than looked up: the threshold may change
-- later, and what justified the decision at the time must remain readable.
--
-- Column for column as docs/database/data-model.md specifies them, checked
-- against PostgreSQL 16. The schema itself is created by V1.
-- =============================================================================

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

  CONSTRAINT uq_offered_skills_tutor_item UNIQUE (tenant_id, tutor_id, catalog_item_id),
  CONSTRAINT ck_offered_skills_status CHECK (status IN ('PENDING','ENABLED','REJECTED','WITHDRAWN')),
  CONSTRAINT ck_offered_skills_enabled_has_path CHECK (status <> 'ENABLED' OR accreditation_path IS NOT NULL),
  CONSTRAINT ck_offered_skills_accreditation_path CHECK (accreditation_path IS NULL OR accreditation_path IN ('ACADEMIC_RECORD','REVIEWED_EVIDENCE'))
);

COMMENT ON COLUMN skills.offered_skills.accredited_grade IS
  'The grade that enabled it, when applicable. Copied rather than looked up: the threshold may change later, and what justified the decision at the time must remain readable.';

CREATE INDEX ix_offered_skills_enabled_by_item
  ON skills.offered_skills (tenant_id, catalog_item_id)
  WHERE status = 'ENABLED';
