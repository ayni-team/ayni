-- audit: append only activity log and detected anomalies
CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.activity_log (
  id             uuid         PRIMARY KEY,
  tenant_id      varchar(32)  NOT NULL,
  actor_id       uuid,
  actor_role     varchar(16)  NOT NULL,
  action         varchar(64)  NOT NULL,
  resource_type  varchar(32)  NOT NULL,
  resource_id    uuid,
  metadata       jsonb,
  occurred_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_activity_log_recent ON audit.activity_log (tenant_id, occurred_at DESC);
CREATE INDEX ix_activity_log_resource ON audit.activity_log (tenant_id, resource_type, resource_id);

CREATE TABLE audit.anomalies (
  id           uuid         PRIMARY KEY,
  tenant_id    varchar(32)  NOT NULL,
  kind         varchar(32)  NOT NULL,
  subject_id   uuid         NOT NULL,
  severity     varchar(8)   NOT NULL,
  detail       jsonb,
  status       varchar(16)  NOT NULL,
  resolved_by  uuid,
  resolved_at  timestamptz,
  detected_at  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_anomalies_kind CHECK (kind IN ('REPEATED_PAIR','UNUSUAL_FREQUENCY','SHORT_DURATION','FAILED_PRESENCE','NO_BOARD_ACTIVITY')),
  CONSTRAINT ck_anomalies_severity CHECK (severity IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_anomalies_status CHECK (status IN ('OPEN','DISMISSED','CONFIRMED'))
);
CREATE INDEX ix_anomalies_queue ON audit.anomalies (tenant_id, status, severity);

CREATE TABLE audit.anomaly_sessions (
  anomaly_id  uuid         NOT NULL REFERENCES audit.anomalies(id),
  session_id  uuid         NOT NULL,
  tenant_id   varchar(32)  NOT NULL,
  CONSTRAINT pk_anomaly_sessions PRIMARY KEY (anomaly_id, session_id)
);
