-- The student whose tutor did not show up is told so (US06). The CHECK keeps every kind the table
-- already allowed, including PURCHASE_CONFIRMED, and adds TUTOR_NO_SHOW.
ALTER TABLE notifications.notifications
    DROP CONSTRAINT ck_notifications_kind;

ALTER TABLE notifications.notifications
    ADD CONSTRAINT ck_notifications_kind
    CHECK (kind IN ('ACCESS_LINK', 'BOOKING_CONFIRMED', 'SESSION_REMINDER',
                    'PRESENCE_CODE', 'CREDITS_EXPIRING', 'PURCHASE_CONFIRMED',
                    'TUTOR_NO_SHOW', 'REQUEST_RESOLVED'));
