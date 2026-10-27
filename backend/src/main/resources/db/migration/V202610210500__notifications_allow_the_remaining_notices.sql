-- The notices that were still missing (US05, US03, US26, US29, US11, US16, US43): a cancelled
-- booking, a closed session and a resolved validation. The CHECK keeps every kind the table
-- already allowed and adds BOOKING_CANCELLED, SESSION_CLOSED and VALIDATION_RESOLVED.
ALTER TABLE notifications.notifications
    DROP CONSTRAINT ck_notifications_kind;

ALTER TABLE notifications.notifications
    ADD CONSTRAINT ck_notifications_kind
    CHECK (kind IN ('ACCESS_LINK', 'BOOKING_CONFIRMED', 'SESSION_REMINDER',
                    'PRESENCE_CODE', 'CREDITS_EXPIRING', 'PURCHASE_CONFIRMED',
                    'TUTOR_NO_SHOW', 'REQUEST_RESOLVED', 'BOOKING_CANCELLED',
                    'SESSION_CLOSED', 'VALIDATION_RESOLVED'));
