-- =============================================================================
-- Matching available offers
-- =============================================================================
-- The search reads only this table. One row per hour a tutor is free and per
-- course that tutor has enabled: availability belongs to the tutor, not to a
-- course, so the same block appears once for every course they can teach.
--
-- A projection, not a source of truth. booking, skills, identity and reputation
-- own every value here; matching copies them when their events arrive, so the
-- search never joins another schema. Booking still validates every hold and
-- booking against its own tables, which is what makes a stale row harmless.
--
-- average_stars is NULL while the tutor has fewer than three ratings in the
-- course: reputation shows no average then, only the new tutor mark.
-- =============================================================================

CREATE TABLE matching.available_offers (
    tenant_id          varchar(32)   NOT NULL,
    block_id           uuid          NOT NULL,
    tutor_id           uuid          NOT NULL,
    catalog_item_id    uuid          NOT NULL,
    starts_at          timestamptz   NOT NULL,
    tutor_name         varchar(160)  NOT NULL,
    average_stars      numeric(3,2),
    ratings_count      integer       NOT NULL DEFAULT 0,
    sessions_taught    integer       NOT NULL DEFAULT 0,

    CONSTRAINT pk_available_offers
        PRIMARY KEY (tenant_id, block_id, catalog_item_id),
    CONSTRAINT ck_available_offers_average_stars
        CHECK (average_stars IS NULL OR average_stars BETWEEN 1 AND 5),
    CONSTRAINT ck_available_offers_counts
        CHECK (ratings_count >= 0 AND sessions_taught >= 0)
);

-- The search: one course of one university, in time order
CREATE INDEX ix_available_offers_search
    ON matching.available_offers (tenant_id, catalog_item_id, starts_at);
