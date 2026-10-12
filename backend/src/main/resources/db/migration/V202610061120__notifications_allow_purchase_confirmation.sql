ALTER TABLE notifications.notifications
  DROP CONSTRAINT ck_notifications_kind;

ALTER TABLE notifications.notifications
  ADD CONSTRAINT ck_notifications_kind
  CHECK (kind IN ('ACCESS_LINK', 'BOOKING_CONFIRMED', 'SESSION_REMINDER',
                  'PRESENCE_CODE', 'CREDITS_EXPIRING', 'PURCHASE_CONFIRMED',
                  'REQUEST_RESOLVED'));
