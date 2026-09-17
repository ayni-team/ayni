-- matching: read projection that makes tutor search fast
CREATE SCHEMA IF NOT EXISTS matching;

CREATE TABLE matching.available_offers (
  tenant_id        varchar(32)   NOT NULL,
  block_id         uuid          NOT NULL,
  tutor_id         uuid          NOT NULL,
  catalog_item_id  uuid          NOT NULL,
  starts_at        timestamptz   NOT NULL,
  tutor_name       varchar(160)  NOT NULL,
  average_stars    numeric(3,2),
  ratings_count    integer       NOT NULL DEFAULT 0,
  sessions_taught  integer       NOT NULL DEFAULT 0,
  CONSTRAINT pk_available_offers PRIMARY KEY (tenant_id, block_id, catalog_item_id)
);
CREATE INDEX ix_available_offers_search ON matching.available_offers (tenant_id, catalog_item_id, starts_at);
