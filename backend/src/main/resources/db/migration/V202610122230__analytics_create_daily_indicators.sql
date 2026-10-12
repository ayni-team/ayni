-- =============================================================================
-- Analytics: daily indicators and what is needed to count them once
-- =============================================================================
-- daily_indicators is the table of data-model.md, one row per university per
-- day, where the day is the one of the university's own time zone.
--
-- The two other tables are not read by any endpoint. They exist because the
-- events arrive in memory, may be delivered twice, and one student takes part in
-- several sessions a day: a bare counter could not tell a repeated delivery from
-- a new fact, nor a student already counted today from one that is not. They
-- hold identifiers and nothing else: no name, no email, no student code.
-- =============================================================================

CREATE TABLE analytics.daily_indicators (
    tenant_id           varchar(32)   NOT NULL,
    day                 date          NOT NULL,
    sessions_completed  integer       NOT NULL DEFAULT 0,
    hours_exchanged     integer       NOT NULL DEFAULT 0,
    active_students     integer       NOT NULL DEFAULT 0,
    new_students        integer       NOT NULL DEFAULT 0,
    requests_approved   integer       NOT NULL DEFAULT 0,

    CONSTRAINT pk_daily_indicators
        PRIMARY KEY (tenant_id, day),
    CONSTRAINT ck_daily_indicators_not_negative
        CHECK (sessions_completed >= 0 AND hours_exchanged >= 0 AND active_students >= 0
               AND new_students >= 0 AND requests_approved >= 0)
);

-- A fact that was already counted: a session, a student that activated, a
-- recognition request that was approved. The key is the identifier of the
-- source of the event, so the same event delivered twice finds it here.
CREATE TABLE analytics.counted_events (
    tenant_id   varchar(32)   NOT NULL,
    kind        varchar(32)   NOT NULL,
    source_id   uuid          NOT NULL,
    counted_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_counted_events
        PRIMARY KEY (tenant_id, kind, source_id),
    CONSTRAINT ck_counted_events_kind
        CHECK (kind IN ('SESSION_COMPLETED','STUDENT_ACTIVATED','RECOGNITION_APPROVED'))
);

-- A student who took part in a session of that day. It is what makes a student
-- count once a day however many sessions they had, and what lets a period
-- count distinct people instead of adding up its days.
CREATE TABLE analytics.active_student_days (
    tenant_id   varchar(32)   NOT NULL,
    day         date          NOT NULL,
    student_id  uuid          NOT NULL,

    CONSTRAINT pk_active_student_days
        PRIMARY KEY (tenant_id, day, student_id)
);
