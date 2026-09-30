-- A compliance-history entry is written only when the student checked in and the tutor did not.
CREATE TABLE reputation.tutor_no_shows (
    session_id       uuid          PRIMARY KEY,
    tenant_id        varchar(32)   NOT NULL,
    tutor_id         uuid          NOT NULL,
    catalog_item_id  uuid          NOT NULL,
    occurred_on      timestamptz   NOT NULL
);

CREATE INDEX ix_tutor_no_shows_history
    ON reputation.tutor_no_shows (tenant_id, tutor_id, occurred_on DESC);

ALTER TABLE notifications.notifications
    DROP CONSTRAINT ck_notifications_kind;

ALTER TABLE notifications.notifications
    ADD CONSTRAINT ck_notifications_kind
    CHECK (kind IN ('ACCESS_LINK', 'BOOKING_CONFIRMED', 'SESSION_REMINDER',
                    'PRESENCE_CODE', 'TUTOR_NO_SHOW', 'CREDITS_EXPIRING',
                    'REQUEST_RESOLVED'));
