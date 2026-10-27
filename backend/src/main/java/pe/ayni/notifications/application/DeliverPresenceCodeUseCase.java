package pe.ayni.notifications.application;

import java.time.Clock;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.PresenceCodeIssued;

/**
 * US54: the presence code reaches the participant's institutional mailbox.
 *
 * <p>Sessions keeps only the hash, so this email is the only copy of the code. The event names the
 * participant, not their address, which is identity's: it is read through {@link IdentityApi}, the
 * one thing this module asks another for.
 *
 * <p>Delivered like the access link: the notice is recorded first, then the email is sent, then the
 * outcome is recorded, and a failure is written down rather than thrown. The notice keeps the
 * session and the expiry, never the code, so a failed code cannot be retried: there is nothing
 * stored to retry it with, exactly as with an access link.
 *
 * <p>No {@code @Transactional} here, for the same reason as {@link DeliverAccessLinkUseCase}.
 */
@Service
public class DeliverPresenceCodeUseCase {

  private static final Logger log = LoggerFactory.getLogger(DeliverPresenceCodeUseCase.class);

  private final NotificationLog notifications;
  private final EmailDelivery delivery;
  private final IdentityApi identity;
  private final Clock clock;

  DeliverPresenceCodeUseCase(
      NotificationLog notifications, EmailDelivery delivery, IdentityApi identity, Clock clock) {
    this.notifications = notifications;
    this.delivery = delivery;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * @return the notice, whose outcome says whether the email left
   * @throws NoSuchElementException when the participant does not exist in the university, in which
   *     case there is no address to write to and nothing is recorded
   */
  public UUID execute(PresenceCodeIssued issued) {
    UserView participant = identity.requireUser(issued.userId());

    Notification notice =
        notifications.record(
            Notification.pending(
                UUID.randomUUID(),
                issued.tenantId(),
                participant.id(),
                participant.email(),
                NotificationKind.PRESENCE_CODE,
                // Which session and until when. Never the code itself.
                Map.of(
                    "sessionId", issued.sessionId().toString(),
                    "expiresAt", issued.expiresAt().toString()),
                clock.instant()));

    try {
      delivery.send(PresenceCodeEmail.of(issued, participant.email()));
      notifications.markSent(notice.getId());
    } catch (EmailNotDelivered failure) {
      // The address is personal data and the code a key: neither goes to the log.
      log.warn("Presence code notice {} was not delivered: {}", notice.getId(), failure.getMessage());
      notifications.markFailed(notice.getId(), failure.getMessage());
    }
    return notice.getId();
  }
}
