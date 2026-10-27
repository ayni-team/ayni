-- =============================================================================
-- Skills: tools that a student proposes because the catalogue does not have them
-- =============================================================================
-- A student who masters a tool that is not in the catalogue proposes it instead of being left with
-- no way to record it (US42). A moderator resolves the proposal later (US43): approving it adds the
-- tool to the catalogue and rejecting it leaves a reason the student can read.
--
-- This table is not in docs/database/data-model.md yet: the pull request proposes it. It follows the
-- shape of skills.validation_requests, which is the other thing in this module that someone decides
-- once. The resolution columns exist from the start so that resolving a proposal (US43) adds
-- behaviour and not another migration.
--
-- The schema itself is created by V1.
-- =============================================================================

CREATE TABLE skills.skill_proposals (
  id               uuid          PRIMARY KEY,
  tenant_id        varchar(32)   NOT NULL,
  proposed_by      uuid          NOT NULL,
  category_id      uuid          NOT NULL REFERENCES skills.categories(id),
  name             varchar(160)  NOT NULL,
  description      varchar(500),
  status           varchar(16)   NOT NULL,
  resolved_by      uuid,
  resolved_at      timestamptz,
  decision_reason  varchar(500),
  catalog_item_id  uuid          REFERENCES skills.catalog_items(id),
  created_at       timestamptz   NOT NULL DEFAULT now(),

  CONSTRAINT ck_skill_proposals_status CHECK (status IN ('PROPOSED','APPROVED','REJECTED')),
  CONSTRAINT ck_skill_proposals_resolved
    CHECK (status = 'PROPOSED' OR (resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
);

COMMENT ON COLUMN skills.skill_proposals.catalog_item_id IS
  'The catalogue item created when the proposal was approved. NULL until then.';

-- A student cannot have the same proposal waiting twice. The use case checks it first and answers
-- with a message; this index is what holds when two requests arrive at once.
CREATE UNIQUE INDEX ux_skill_proposals_waiting
  ON skills.skill_proposals (tenant_id, proposed_by, lower(name))
  WHERE status = 'PROPOSED';

-- The moderator's queue: only what still waits.
CREATE INDEX ix_skill_proposals_waiting
  ON skills.skill_proposals (tenant_id, created_at)
  WHERE status = 'PROPOSED';

-- A student reads their own proposals, newest first.
CREATE INDEX ix_skill_proposals_proposer
  ON skills.skill_proposals (tenant_id, proposed_by, created_at DESC);
