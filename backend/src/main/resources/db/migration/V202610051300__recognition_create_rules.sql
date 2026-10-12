-- =============================================================================
-- Recognition: what a university asks of a student before it will consider recognising their hours (US27)
-- =============================================================================
-- A student who teaches earns credits, and some universities give extracurricular credit for the
-- hours taught. How many hours they ask for is theirs to decide, so it is a rule of the university,
-- not a constant of the platform.
--
-- A university has one rule in force at a time. A new rule does not overwrite the old one: it is
-- added with the date it starts, and the old one is marked superseded, so a request submitted under
-- the old one can still be explained.
--
-- A university without a rule has not opened recognition: nobody can request it, and nobody is told
-- how many hours they lack.
--
-- minimum_rating is kept as the university states it. Reputation does not hold the ratings of a
-- student across skills yet, so nothing enforces it today.
--
-- The schema itself is created by V1.
-- =============================================================================

CREATE TABLE recognition.rules (
  id              uuid          PRIMARY KEY,
  tenant_id       varchar(32)   NOT NULL,
  minimum_hours   integer       NOT NULL,
  minimum_rating  numeric(3,2),
  valid_from      date          NOT NULL,
  superseded_at   timestamptz,
  created_at      timestamptz   NOT NULL DEFAULT now(),

  CONSTRAINT ck_rules_minimum_hours CHECK (minimum_hours > 0),
  CONSTRAINT ck_rules_minimum_rating CHECK (minimum_rating IS NULL OR (minimum_rating >= 0 AND minimum_rating <= 5))
);

-- The rule in force is read on every progress request: latest start among the ones not superseded.
CREATE INDEX ix_rules_tenant_current
  ON recognition.rules (tenant_id, valid_from DESC)
  WHERE superseded_at IS NULL;

COMMENT ON TABLE recognition.rules IS
  'What a university asks of a student to consider recognising the hours they taught. One in force at a time.';
COMMENT ON COLUMN recognition.rules.minimum_hours IS
  'Hours of verified sessions the student must have taught, which are the credits earned by teaching.';
