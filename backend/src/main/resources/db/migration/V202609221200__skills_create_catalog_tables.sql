-- =============================================================================
-- Skills: categories and catalog items
-- =============================================================================
-- One table for two kinds of thing, told apart by scope: global tools ship with
-- Ayni and are the same everywhere, university courses belong to the university
-- that teaches them. A student sees the global items plus their own
-- university's, which is one condition in the query and not two tables to join.
--
-- Column for column as docs/database/data-model.md specifies them, checked
-- against PostgreSQL 16. The schema itself is created by V1.
-- =============================================================================

CREATE TABLE skills.categories (
  id          uuid          PRIMARY KEY,
  name        varchar(80)   NOT NULL,
  sort_order  smallint      NOT NULL DEFAULT 0,
  CONSTRAINT uq_categories_name UNIQUE (name)
);

COMMENT ON TABLE skills.categories IS
  'Global. Technology, university courses, languages, design.';

CREATE TABLE skills.catalog_items (
  id             uuid          PRIMARY KEY,
  scope          varchar(16)   NOT NULL,
  tenant_id      varchar(32),
  category_id    uuid          NOT NULL REFERENCES skills.categories(id),
  name           varchar(160)  NOT NULL,
  description    varchar(500),
  course_code    varchar(32),
  status         varchar(16)   NOT NULL,
  created_at     timestamptz   NOT NULL DEFAULT now(),

  CONSTRAINT ck_catalog_items_scope CHECK (scope IN ('GLOBAL','UNIVERSITY')),
  CONSTRAINT ck_catalog_items_tenant_matches_scope CHECK ((scope = 'GLOBAL') = (tenant_id IS NULL)),
  CONSTRAINT ck_catalog_items_course_code CHECK (scope = 'GLOBAL' OR course_code IS NOT NULL),
  CONSTRAINT ck_catalog_items_status CHECK (status IN ('ACTIVE','RETIRED')),
  CONSTRAINT uq_catalog_items_tenant_course UNIQUE (tenant_id, course_code)
);

COMMENT ON CONSTRAINT ck_catalog_items_tenant_matches_scope ON skills.catalog_items IS
  'Keeps a global item from ever being assigned to a university, which would quietly make it invisible to everyone else.';

CREATE INDEX ix_catalog_items_tenant_status
  ON skills.catalog_items (tenant_id, status);
