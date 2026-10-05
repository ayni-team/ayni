-- =============================================================================
-- Skills: the minimum grade a university asks of a student to teach a course (US51)
-- =============================================================================
-- Identity registers the university with a minimum teaching grade, which is the value a course is
-- enabled against. A coordinator now sets it themselves, and only skills reads it to decide, so the
-- choice is kept here instead of reaching into the university of another module.
--
-- One row per university, created the first time a coordinator sets the grade. A university with no
-- row keeps the grade it was registered with.
--
-- The grade of a tutor is copied into their offer when it is enabled (accredited_grade), so changing
-- this value never touches an offer that was already granted.
--
-- The schema itself is created by V1.
-- =============================================================================

CREATE TABLE skills.academic_settings (
  tenant_id               varchar(32)   PRIMARY KEY,
  minimum_teaching_grade  numeric(4,2)  NOT NULL,
  updated_by              uuid          NOT NULL,
  updated_at              timestamptz   NOT NULL,

  CONSTRAINT ck_academic_settings_grade_scale
    CHECK (minimum_teaching_grade >= 0 AND minimum_teaching_grade <= 20)
);

COMMENT ON TABLE skills.academic_settings IS
  'What a university decides about its courses. Absent until a coordinator sets something: the grade then comes from identity.tenants.';
COMMENT ON COLUMN skills.academic_settings.minimum_teaching_grade IS
  'Grade a student needs in a course to teach it, on the 0 to 20 scale. Applies to new enablements only.';
