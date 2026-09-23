-- =============================================================================
-- Skills: what a student wants to receive
-- =============================================================================
-- What drives the recommendations shown on the portal, and the "need help with" half of
-- US40's initial configuration. The "can teach" half declares no interest of its own: it goes
-- straight through the same academic-record path US13 already built, in skills.offered_skills.
--
-- Column for column as docs/database/data-model.md specifies them, checked against PostgreSQL 16.
-- The schema itself is created by V1.
-- =============================================================================

CREATE TABLE skills.learning_interests (
  id                uuid          PRIMARY KEY,
  tenant_id         varchar(32)   NOT NULL,
  student_id        uuid          NOT NULL,
  catalog_item_id   uuid          NOT NULL REFERENCES skills.catalog_items(id),
  created_at        timestamptz   NOT NULL DEFAULT now(),

  CONSTRAINT uq_learning_interests_student_item UNIQUE (tenant_id, student_id, catalog_item_id)
);

CREATE INDEX ix_learning_interests_student
  ON skills.learning_interests (tenant_id, student_id);
