package pe.ayni.notifications.application;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.notifications.domain.model.Notification;

/**
 * What every notice that follows an event has in common: find the person, write the notice before
 * the email leaves, send it and say how it went.
 *
 * <p>Three things it guarantees. The same notice is never written twice, so an event delivered a
 * second time tells nobody again. A recipient identity does not know is logged by identifier and
 * skipped, and the next recipient is still told. And an email the carrier refuses is recorded on its
 * notice instead of failing the listener. Neither the address nor the reason of a refusal reaches
 * a log; the reason is kept on the notice, which only the delivery reads.
 */
@Component
class NoticeSender {

  private static final Logger log = LoggerFactory.getLogger(NoticeSender.class);

  private final NotificationLog notifications;
  private final EmailDelivery delivery;
  private final IdentityApi identity;
  private final Clock clock;

  NoticeSender(
      NotificationLog notifications, EmailDelivery delivery, IdentityApi identity, Clock clock) {
    this.notifications = notifications;
    this.delivery = delivery;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * Delivers each notice on its own: one that cannot be written does not hold back the others. When
   * any could not be written the first failure is thrown once all were tried, so the event is
   * delivered again and the notices already written are found and skipped.
   */
  void deliver(List<NoticeToSend> notices) {
    RuntimeException firstFailure = null;
    for (NoticeToSend notice : notices) {
      try {
        deliverOne(notice);
      } catch (RuntimeException failure) {
        log.error(
            "The {} notice for {} could not be recorded: {}",
            notice.kind(),
            notice.eventKey(),
            failure.getClass().getSimpleName());
        if (firstFailure == null) {
          firstFailure = failure;
        }
      }
    }
    if (firstFailure != null) {
      throw firstFailure;
    }
  }

  /**
   * The zone of the university, which is how the time of a tutoring reads in its email. Read from
   * identity and never from the machine; when identity cannot say, UTC, which the email then names.
   */
  ZoneId universityZone(String tenantId) {
    try {
      return ZoneId.of(identity.requireTenant(tenantId).timezone());
    } catch (RuntimeException unknown) {
      log.warn("The zone of university {} is unknown, so UTC is used in its emails", tenantId);
      return ZoneOffset.UTC;
    }
  }

  private void deliverOne(NoticeToSend notice) {
    UserView recipient;
    try {
      recipient = identity.requireUser(notice.recipientId());
    } catch (NoSuchElementException unknown) {
      recipient = null;
    }
    // Identity throws for somebody it does not know; an answer of nobody is read the same way.
    if (recipient == null) {
      log.warn(
          "No {} notice for {}: recipient {} does not exist",
          notice.kind(),
          notice.eventKey(),
          notice.recipientId());
      return;
    }

    Notification candidate =
        Notification.pending(
            NoticeId.of(notice.kind(), notice.eventKey(), recipient.id()),
            notice.tenantId(),
            recipient.id(),
            recipient.email(),
            notice.kind(),
            notice.payload(),
            clock.instant());
    Optional<Notification> toDeliver;
    try {
      toDeliver = notifications.recordOnce(candidate);
    } catch (DataIntegrityViolationException twin) {
      // Another delivery of the same event wrote it between our look and our write.
      log.info(
          "The {} notice for {} was written by another delivery",
          notice.kind(),
          notice.eventKey());
      return;
    }
    if (toDeliver.isEmpty()) {
      log.info("The {} notice for {} was already delivered", notice.kind(), notice.eventKey());
      return;
    }

    Notification written = toDeliver.get();
    try {
      delivery.send(notice.email().apply(written.getRecipientEmail()));
      notifications.markSent(written.getId());
    } catch (EmailNotDelivered failure) {
      log.warn("Notice {} ({}) was not delivered", written.getId(), notice.kind());
      notifications.markFailed(written.getId(), failure.getMessage());
    }
  }
}
