-- =============================================================================
-- Audit: anomalies
-- =============================================================================
-- A pattern that suggests credits are being accumulated artificially, kept so a
-- coordinator can look at it before recognising hours that nothing supports.
--
-- subject_id is the tutor whose credits the pattern puts in question. The other
-- people involved, and the figures that made it an anomaly, are in detail.
--
-- resolution_reason is not in the data model of the first iteration, which had
-- nowhere to keep why a coordinator dismissed an alert. It is the free text of
-- the decision, so it stays here and is not copied to the activity log.
--
-- A dismissed alert is always attributed: who and when are required by the
-- table too, not only by the application.
-- =============================================================================

CREATE TABLE audit.anomalies (
    id                 uuid          NOT NULL,
    tenant_id          varchar(32)   NOT NULL,
    kind               varchar(32)   NOT NULL,
    subject_id         uuid          NOT NULL,
    severity           varchar(8)    NOT NULL,
    detail             jsonb,
    status             varchar(16)   NOT NULL,
    resolved_by        uuid,
    resolved_at        timestamptz,
    resolution_reason  varchar(500),
    detected_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_anomalies
        PRIMARY KEY (id),
    CONSTRAINT ck_anomalies_kind
        CHECK (kind IN ('REPEATED_PAIR','UNUSUAL_FREQUENCY','SHORT_DURATION',
                        'FAILED_PRESENCE','NO_BOARD_ACTIVITY')),
    CONSTRAINT ck_anomalies_severity
        CHECK (severity IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_anomalies_status
        CHECK (status IN ('OPEN','DISMISSED','CONFIRMED')),
    CONSTRAINT ck_anomalies_resolution
        CHECK (status = 'OPEN'
               OR (resolved_by IS NOT NULL AND resolved_at IS NOT NULL)),
    CONSTRAINT ck_anomalies_dismissal_reason
        CHECK (status <> 'DISMISSED' OR resolution_reason IS NOT NULL)
);

CREATE INDEX ix_anomalies_tenant_status_severity
    ON audit.anomalies (tenant_id, status, severity);

CREATE TABLE audit.anomaly_sessions (
    anomaly_id  uuid          NOT NULL,
    session_id  uuid          NOT NULL,
    tenant_id   varchar(32)   NOT NULL,

    CONSTRAINT pk_anomaly_sessions
        PRIMARY KEY (anomaly_id, session_id),
    CONSTRAINT fk_anomaly_sessions_anomaly
        FOREIGN KEY (anomaly_id) REFERENCES audit.anomalies (id)
);

-- Recognition asks which open alerts a set of sessions has, which is a lookup by
-- session and not by anomaly.
CREATE INDEX ix_anomaly_sessions_session
    ON audit.anomaly_sessions (tenant_id, session_id);
