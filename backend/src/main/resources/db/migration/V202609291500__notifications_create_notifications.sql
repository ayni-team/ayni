-- =============================================================================
-- Notifications
-- =============================================================================
-- One row per notice delivered to somebody. The row is written, and committed,
-- before the email leaves, and then marked sent or failed: a notice that never
-- arrived has to be visible, because getting in and confirming presence both
-- depend on an email arriving.
--
-- recipient_id is nullable, unlike data-model.md, because the first notice of
-- all has nobody to point at: an activation link goes to an email that has no
-- account yet, and AccessRequested carries the email, not a user. The address
-- is always known, which is why recipient_email stays NOT NULL.
--
-- tenant_id is nullable as in the data model: a platform administrator's
-- access link belongs to no university.
--
-- payload never holds a secret. An access link is delivered and forgotten;
-- what is stored is what it was for and when it expires.
-- =============================================================================

CREATE TABLE notifications.notifications (
    id               uuid          NOT NULL,
    tenant_id        varchar(32),
    recipient_id     uuid,
    recipient_email  varchar(160)  NOT NULL,
    kind             varchar(40)   NOT NULL,
    payload          jsonb         NOT NULL,
    sent_at          timestamptz,
    failed_reason    varchar(500),
    read_at          timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_notifications
        PRIMARY KEY (id),
    CONSTRAINT ck_notifications_kind
        CHECK (kind IN ('ACCESS_LINK', 'BOOKING_CONFIRMED', 'SESSION_REMINDER',
                        'PRESENCE_CODE', 'CREDITS_EXPIRING', 'REQUEST_RESOLVED')),
    -- Sent or failed, never both.
    CONSTRAINT ck_notifications_outcome
        CHECK (sent_at IS NULL OR failed_reason IS NULL)
);

-- A person's own notices, newest first
CREATE INDEX ix_notifications_recipient
    ON notifications.notifications (tenant_id, recipient_id, created_at DESC);

-- Notices that have not left yet, by kind
CREATE INDEX ix_notifications_unsent
    ON notifications.notifications (kind)
    WHERE sent_at IS NULL;
