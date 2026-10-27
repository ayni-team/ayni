-- =============================================================================
-- Analytics: the searches that found no hour inside the window asked for (US53)
-- =============================================================================
-- uncovered_demand is the table of data-model.md without offers_available: the
-- event that feeds it, DemandUncovered, says which course was searched for and
-- when, not what the search found around it, so there is nothing to fill that
-- column with. One row per university, course and day, where the day is the one
-- of the university's own time zone. It counts searches and holds no person:
-- the event does not carry one.
-- =============================================================================

CREATE TABLE analytics.uncovered_demand (
    tenant_id        varchar(32)   NOT NULL,
    catalog_item_id  uuid          NOT NULL,
    day              date          NOT NULL,
    searches         integer       NOT NULL DEFAULT 0,

    CONSTRAINT pk_uncovered_demand
        PRIMARY KEY (tenant_id, catalog_item_id, day),
    CONSTRAINT ck_uncovered_demand_not_negative
        CHECK (searches >= 0)
);

ALTER TABLE analytics.uncovered_demand ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analytics.uncovered_demand
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));
