-- analytics: usage indicators per university, aggregates only
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.daily_indicators (
  tenant_id           varchar(32)  NOT NULL,
  day                 date         NOT NULL,
  sessions_completed  integer      NOT NULL DEFAULT 0,
  hours_exchanged     integer      NOT NULL DEFAULT 0,
  active_students     integer      NOT NULL DEFAULT 0,
  new_students        integer      NOT NULL DEFAULT 0,
  requests_approved   integer      NOT NULL DEFAULT 0,
  CONSTRAINT pk_daily_indicators PRIMARY KEY (tenant_id, day)
);

CREATE TABLE analytics.uncovered_demand (
  tenant_id         varchar(32)  NOT NULL,
  catalog_item_id   uuid         NOT NULL,
  day               date         NOT NULL,
  searches          integer      NOT NULL DEFAULT 0,
  offers_available  integer      NOT NULL DEFAULT 0,
  CONSTRAINT pk_uncovered_demand PRIMARY KEY (tenant_id, catalog_item_id, day)
);
