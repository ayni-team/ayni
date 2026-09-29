package pe.ayni.notifications.application;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.infrastructure.NotificationRepository;

/**
 * Writes a notice and its outcome, each in a transaction of its own.
 *
 * <p>The notice is committed before the email leaves, so a delivery interrupted halfway, the
 * process stopping between sending and recording, still leaves a pending notice behind instead of
 * nothing. Writing it in the caller's transaction would roll it back with everything else.
 */
@Component
class NotificationLog {

  private final NotificationRepository notifications;
  private final Clock clock;

  NotificationLog(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Notification record(Notification notification) {
    return notifications.save(notification);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markSent(UUID notificationId) {
    find(notificationId).markSent(clock.instant());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(UUID notificationId, String reason) {
    find(notificationId).markFailed(reason);
  }

  private Notification find(UUID notificationId) {
    return notifications
        .findById(notificationId)
        .orElseThrow(() -> new NoSuchElementException("Notification not found: " + notificationId));
  }
}
