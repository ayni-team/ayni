package pe.ayni.notifications.domain.model;

import java.util.Arrays;
import java.util.List;

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
  TUTOR_NO_SHOW,
  CREDITS_EXPIRING,
  PURCHASE_CONFIRMED,
  REQUEST_RESOLVED;

  /**
   * Whether the recipient sees it in the application. A link or a code travels by email and
   * carries a secret, so it is not a notice of the application and is never listed.
   */
  public boolean isListed() {
    return this != ACCESS_LINK && this != PRESENCE_CODE;
  }

  /** Every kind the recipient sees in the application. */
  public static List<NotificationKind> listed() {
    return Arrays.stream(values()).filter(NotificationKind::isListed).toList();
  }
}
