package pe.ayni.notifications.application;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import pe.ayni.notifications.domain.model.NotificationKind;

/**
 * The identifier of a notice, derived from what it is about so that the same event delivered twice
 * names the same notice and the second delivery finds it already written.
 *
 * <p>The events carry no identifier of their own, so the key is what makes each one happen once: the
 * booking that was confirmed, the session that closed, the request that was decided. The recipient
 * is part of it because one event can tell several people.
 */
final class NoticeId {

  private NoticeId() {}

  static UUID of(NotificationKind kind, String eventKey, UUID recipientId) {
    String name = kind + ":" + eventKey + ":" + recipientId;
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
  }
}
