-- notifications: notices delivered to students, tutors and coordinators
CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TABLE notifications.notifications (
  id               uuid          PRIMARY KEY,
  tenant_id        varchar(32),
  recipient_id     uuid          NOT NULL,
  recipient_email  varchar(160)  NOT NULL,
  kind             varchar(40)   NOT NULL,
  payload          jsonb         NOT NULL,
  sent_at          timestamptz,
  failed_reason    varchar(500),
  read_at          timestamptz,
  created_at       timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_recipient ON notifications.notifications (tenant_id, recipient_id, created_at DESC);
CREATE INDEX ix_notifications_unsent ON notifications.notifications (kind) WHERE sent_at IS NULL;
