package pe.ayni.notifications.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import pe.ayni.notifications.domain.model.NotificationKind;

/**
 * One notice a use case wants delivered: to whom, about which event, what its payload keeps and how
 * the email reads once the address is known.
 *
 * @param eventKey what makes the event happen once, such as the booking or the session, which with
 *     the recipient names the notice
 * @param payload what the application shows about it, never a secret, a free text or an address
 * @param email builds the email from the address read from identity
 */
record NoticeToSend(
    String tenantId,
    UUID recipientId,
    String eventKey,
    NotificationKind kind,
    Map<String, String> payload,
    Function<String, Email> email) {

  NoticeToSend {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(recipientId, "recipientId must not be null");
    Objects.requireNonNull(eventKey, "eventKey must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    payload = Map.copyOf(payload);
    Objects.requireNonNull(email, "email must not be null");
  }
}
