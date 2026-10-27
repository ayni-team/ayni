package pe.ayni.notifications.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.notifications.domain.model.NotificationNotFoundException;
import pe.ayni.notifications.infrastructure.NotificationRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Marks one notice of the person as read. Doing it again changes nothing.
 *
 * <p>Somebody else's notice, another university's and one that does not exist are the same answer:
 * not found. The notice is locked while it is marked, so two requests at once leave the date of the
 * first.
 */
@Service
public class MarkNotificationReadUseCase {

  private final NotificationRepository notifications;
  private final Clock clock;

  MarkNotificationReadUseCase(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  /**
   * @throws NotificationNotFoundException when the person has no such notice in this university
   */
  @Transactional
  public void execute(UUID recipientId, UUID notificationId) {
    Objects.requireNonNull(recipientId, "recipientId must not be null");
    Objects.requireNonNull(notificationId, "notificationId must not be null");

    Notification notice =
        notifications
            .lockOwned(
                TenantContext.require(), recipientId, NotificationKind.listed(), notificationId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));

    notice.markRead(clock.instant());
  }
}
