-- =============================================================================
-- V202609222010 - Reputation: tutor standing projection
-- =============================================================================
-- Read projection with the tutor's standing for each catalogue item.
-- It is not the source of truth: it is rebuilt from reputation ratings.
--
-- The same tutor can have a different standing for every skill they teach.
-- =============================================================================

CREATE TABLE reputation.tutor_standing (
                                           tenant_id        varchar(32)   NOT NULL,
                                           tutor_id         uuid          NOT NULL,
                                           catalog_item_id  uuid          NOT NULL,
                                           sessions_taught  integer       NOT NULL DEFAULT 0,
                                           ratings_count    integer       NOT NULL DEFAULT 0,
                                           average_stars    numeric(3,2),
                                           updated_at       timestamptz   NOT NULL DEFAULT now(),

                                           PRIMARY KEY (tenant_id, tutor_id, catalog_item_id)
);

COMMENT ON TABLE reputation.tutor_standing IS
  'Read projection of a tutor standing for each catalogue item, rebuilt from ratings.';

COMMENT ON COLUMN reputation.tutor_standing.average_stars IS
  'Average rating for the tutor in this catalogue item. Presentation rules decide when it is shown.';