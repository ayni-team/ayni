package pe.ayni.notifications.domain.model;

/**
 * What a notice is about. The same list {@code ck_notifications_kind} accepts, so adding one is a
 * migration as well as a constant.
 */
public enum NotificationKind {

  /** A single use link to sign in, activate an account or accept an invitation. */
  ACCESS_LINK,

  BOOKING_CONFIRMED,
  SESSION_REMINDER,
  PRESENCE_CODE,
  CREDITS_EXPIRING,
  REQUEST_RESOLVED
}
