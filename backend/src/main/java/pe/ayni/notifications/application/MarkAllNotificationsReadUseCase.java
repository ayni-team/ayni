package pe.ayni.notifications.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.notifications.infrastructure.NotificationRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** Marks every unread notice of the person as read, all with the same instant. */
@Service
public class MarkAllNotificationsReadUseCase {

  private final NotificationRepository notifications;
  private final Clock clock;

  MarkAllNotificationsReadUseCase(NotificationRepository notifications, Clock clock) {
    this.notifications = notifications;
    this.clock = clock;
  }

  @Transactional
  public void execute(UUID recipientId) {
    Objects.requireNonNull(recipientId, "recipientId must not be null");

    Instant now = clock.instant();
    notifications
        .lockUnread(TenantContext.require(), recipientId, NotificationKind.listed())
        .forEach(notice -> notice.markRead(now));
  }
}
