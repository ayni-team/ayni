package pe.ayni.notifications.domain.model;

import java.util.UUID;

/** The notice does not exist for this person in this university. */
public class NotificationNotFoundException extends RuntimeException {

  public NotificationNotFoundException(UUID notificationId) {
    super("Notification not found: " + notificationId);
  }
}
