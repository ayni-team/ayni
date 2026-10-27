package pe.ayni.notifications.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.ayni.notifications.application.MyNotices;
import pe.ayni.notifications.application.NoticeView;
import pe.ayni.notifications.domain.model.NotificationKind;

/**
 * A page of the person's notices. It is described with its own fields rather than returned as a
 * Spring {@code Page}, whose shape is the framework's to change.
 */
@Schema(name = "NotificationsPage", description = "The person's notices, newest first")
public record NotificationsPage(
    List<Notice> items,
    @Schema(example = "0") int page,
    @Schema(example = "20") int size,
    @Schema(description = "Notices that match, in all pages", example = "3") long total,
    @Schema(
            description = "Notices not read yet in all, whether or not the filter keeps them",
            example = "2")
        long unreadCount) {

  /**
   * One notice.
   *
   * @param payload what the notice is about; never an address or a secret
   * @param readAt when it was read, {@code null} while it is unread
   */
  @Schema(name = "Notice")
  public record Notice(
      @Schema(example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f") UUID id,
      @Schema(example = "TUTOR_NO_SHOW") NotificationKind kind,
      @Schema(example = "{\"sessionId\": \"d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f\"}")
          Map<String, String> payload,
      @Schema(example = "2026-10-20T14:31:05Z") Instant createdAt,
      @Schema(nullable = true, example = "null") Instant readAt) {}

  static NotificationsPage of(MyNotices notices) {
    return new NotificationsPage(
        notices.items().stream().map(NotificationsPage::describe).toList(),
        notices.page(),
        notices.size(),
        notices.total(),
        notices.unreadCount());
  }

  private static Notice describe(NoticeView view) {
    return new Notice(view.id(), view.kind(), view.payload(), view.createdAt(), view.readAt());
  }
}
