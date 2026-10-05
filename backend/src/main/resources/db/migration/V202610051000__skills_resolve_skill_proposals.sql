-- =============================================================================
-- Skills: a moderator can also join a proposal to a skill the catalogue already has (US43)
-- =============================================================================
-- A proposal is resolved one of three ways: approved (a new item is added to the catalogue), merged
-- into an item that already exists, or rejected with a reason. V202610042100 created the table with
-- the first and the last; this adds the second and makes the table refuse what a resolution must
-- always carry:
--
--   * an approval or a merge points at the catalogue item it ended up in, and
--   * a rejection has the reason the student reads.
--
-- Nothing in the table can be in those states yet, so the new checks cannot fail on existing rows.
-- =============================================================================

ALTER TABLE skills.skill_proposals DROP CONSTRAINT ck_skill_proposals_status;

ALTER TABLE skills.skill_proposals
  ADD CONSTRAINT ck_skill_proposals_status
    CHECK (status IN ('PROPOSED','APPROVED','MERGED','REJECTED'));

ALTER TABLE skills.skill_proposals
  ADD CONSTRAINT ck_skill_proposals_catalog_item
    CHECK (status NOT IN ('APPROVED','MERGED') OR catalog_item_id IS NOT NULL);

ALTER TABLE skills.skill_proposals
  ADD CONSTRAINT ck_skill_proposals_rejection_reason
    CHECK (status <> 'REJECTED' OR decision_reason IS NOT NULL);

COMMENT ON COLUMN skills.skill_proposals.catalog_item_id IS
  'The catalogue item the proposal ended up in: the one an approval created, or the existing one it was merged into. NULL until resolved, and for a rejection.';

-- The moderator's history: what was decided, newest first.
CREATE INDEX ix_skill_proposals_resolved
  ON skills.skill_proposals (tenant_id, resolved_at DESC)
  WHERE status <> 'PROPOSED';
