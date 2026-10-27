package pe.ayni.notifications.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;

/**
 * A notice as its recipient sees it: what it is about and whether they have read it. Neither the
 * address nor how the delivery went is part of it.
 */
public record NoticeView(
    UUID id, NotificationKind kind, Map<String, String> payload, Instant createdAt, Instant readAt) {

  static NoticeView of(Notification notification) {
    return new NoticeView(
        notification.getId(),
        notification.getKind(),
        notification.getPayload(),
        notification.getCreatedAt(),
        notification.getReadAt());
  }
}
