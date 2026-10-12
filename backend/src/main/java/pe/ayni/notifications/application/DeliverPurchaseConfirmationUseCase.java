package pe.ayni.notifications.application;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.PurchaseConfirmed;

@Service
public class DeliverPurchaseConfirmationUseCase {

  private static final Logger log =
      LoggerFactory.getLogger(DeliverPurchaseConfirmationUseCase.class);

  private final NotificationLog notifications;
  private final EmailDelivery delivery;
  private final IdentityApi identity;
  private final Clock clock;

  DeliverPurchaseConfirmationUseCase(
      NotificationLog notifications, EmailDelivery delivery, IdentityApi identity, Clock clock) {
    this.notifications = notifications;
    this.delivery = delivery;
    this.identity = identity;
    this.clock = clock;
  }

  public UUID execute(PurchaseConfirmed purchase) {
    UserView student = identity.requireUser(purchase.studentId());
    Notification notice =
        notifications.record(
            Notification.pending(
                UUID.randomUUID(),
                purchase.tenantId(),
                student.id(),
                student.email(),
                NotificationKind.PURCHASE_CONFIRMED,
                Map.of(
                    "purchaseId", purchase.purchaseId().toString(),
                    "credits", Integer.toString(purchase.credits().amount()),
                    "occurredOn", purchase.occurredOn().toString()),
                clock.instant()));

    try {
      delivery.send(PurchaseConfirmedEmail.of(purchase, student.email()));
      notifications.markSent(notice.getId());
    } catch (EmailNotDelivered failure) {
      log.warn(
          "Purchase confirmation notice {} was not delivered: {}",
          notice.getId(),
          failure.getMessage());
      notifications.markFailed(notice.getId(), failure.getMessage());
    }
    return notice.getId();
  }
}
