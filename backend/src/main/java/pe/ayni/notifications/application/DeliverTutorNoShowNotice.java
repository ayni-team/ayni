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
import pe.ayni.shared.events.SessionAbandoned;

/** Delivers the no-show notice to the student who checked in. */
@Service
public class DeliverTutorNoShowNotice {

  private static final Logger log = LoggerFactory.getLogger(DeliverTutorNoShowNotice.class);

  private final NotificationLog notifications;
  private final EmailDelivery delivery;
  private final IdentityApi identity;
  private final Clock clock;

  DeliverTutorNoShowNotice(
      NotificationLog notifications, EmailDelivery delivery, IdentityApi identity, Clock clock) {
    this.notifications = notifications;
    this.delivery = delivery;
    this.identity = identity;
    this.clock = clock;
  }

  public void execute(SessionAbandoned event) {
    if (!event.studentCheckedIn()) {
      throw new IllegalArgumentException("Student must have checked in for a no-show notice");
    }
    UserView student = identity.requireUser(event.studentId());
    Notification notice =
        notifications.record(
            Notification.pending(
                UUID.randomUUID(),
                event.tenantId(),
                student.id(),
                student.email(),
                NotificationKind.TUTOR_NO_SHOW,
                Map.of("sessionId", event.sessionId().toString()),
                clock.instant()));
    try {
      delivery.send(TutorNoShowEmail.to(student.email()));
      notifications.markSent(notice.getId());
    } catch (EmailNotDelivered failure) {
      log.warn("Tutor no-show notice {} was not delivered: {}", notice.getId(), failure.getMessage());
      notifications.markFailed(notice.getId(), failure.getMessage());
    }
  }
}
