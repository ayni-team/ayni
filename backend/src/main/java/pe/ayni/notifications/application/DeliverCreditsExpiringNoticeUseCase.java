package pe.ayni.notifications.application;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.shared.events.CreditsExpiring;

/**
 * Tells a student that a group of their credits is about to expire (US26).
 *
 * <p>Wallet publishes one event per group, once. The group is named by the student, the expiry, the
 * amount and the moment the job ran, which an event delivered again repeats.
 */
@Service
public class DeliverCreditsExpiringNoticeUseCase {

  private final NoticeSender sender;

  DeliverCreditsExpiringNoticeUseCase(NoticeSender sender) {
    this.sender = sender;
  }

  public void execute(CreditsExpiring expiring) {
    ZoneId zone = sender.universityZone(expiring.tenantId());
    sender.deliver(
        List.of(
            new NoticeToSend(
                expiring.tenantId(),
                expiring.userId(),
                "credits-expiring:%s:%s:%d:%s"
                    .formatted(
                        expiring.userId(),
                        expiring.expiresAt(),
                        expiring.amount().amount(),
                        expiring.occurredOn()),
                NotificationKind.CREDITS_EXPIRING,
                Map.of(
                    "amount", Integer.toString(expiring.amount().amount()),
                    "expiresAt", expiring.expiresAt().toString()),
                address -> CreditsExpiringEmail.of(expiring, zone, address))));
  }
}
