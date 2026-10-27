package pe.ayni.notifications.application;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.AccessRequested;

/**
 * US38: the access link reaches the inbox it was asked for.
 *
 * <p>Identity only keeps the hash of the token, so this email is the one and only copy of the link.
 * The notice is recorded first, then the email is sent, then the outcome is recorded; a failure is
 * written down and never thrown, because the student who asked is told to check their email either
 * way, and an exception here would only be logged by the event infrastructure and forgotten.
 *
 * <p>A failed access link is not retried: the link is not stored anywhere to retry it with, and it
 * expires in minutes. Asking again issues a new one.
 *
 * <p>No {@code @Transactional} here on purpose: every write goes through {@link NotificationLog},
 * which commits it on its own, so the notice is in the database before the email leaves whatever
 * transaction the caller has open. Joining the caller's would roll the notice back with it.
 */
@Service
public class DeliverAccessLinkUseCase {

  private static final Logger log = LoggerFactory.getLogger(DeliverAccessLinkUseCase.class);

  private final NotificationLog notifications;
  private final EmailDelivery delivery;
  private final Clock clock;

  DeliverAccessLinkUseCase(NotificationLog notifications, EmailDelivery delivery, Clock clock) {
    this.notifications = notifications;
    this.delivery = delivery;
    this.clock = clock;
  }

  /** @return the notice, whose outcome says whether the email left */
  public UUID execute(AccessRequested request) {
    Notification notice =
        notifications.record(
            Notification.pending(
                UUID.randomUUID(),
                request.tenantId(),
                null,
                request.email(),
                NotificationKind.ACCESS_LINK,
                // What the link was for and when it dies. Never the link itself.
                Map.of(
                    "purpose", request.purpose(),
                    "expiresAt", request.expiresAt().toString()),
                clock.instant()));

    try {
      delivery.send(AccessLinkEmail.of(request));
      notifications.markSent(notice.getId());
    } catch (EmailNotDelivered failure) {
      // The address is personal data and the link a key: neither goes to the log.
      log.warn("Access link notice {} was not delivered: {}", notice.getId(), failure.getMessage());
      notifications.markFailed(notice.getId(), failure.getMessage());
    }
    return notice.getId();
  }
}
