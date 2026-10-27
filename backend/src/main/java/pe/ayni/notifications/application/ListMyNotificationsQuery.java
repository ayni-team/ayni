package pe.ayni.notifications.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;
import pe.ayni.notifications.infrastructure.NotificationRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The notices of the application for one person, newest first.
 *
 * <p>The list belongs to the application, not to the delivery: a notice whose email failed is
 * listed all the same. Links and codes are not, since they are emailed secrets and not notices.
 */
@Service
public class ListMyNotificationsQuery {

  private final NotificationRepository notifications;

  ListMyNotificationsQuery(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  /**
   * @param unreadOnly keeps the notices that were not read yet; the unread count ignores it
   */
  @Transactional(readOnly = true)
  public MyNotices execute(UUID recipientId, boolean unreadOnly, int page, int size) {
    Objects.requireNonNull(recipientId, "recipientId must not be null");
    String tenantId = TenantContext.require();

    Page<Notification> found =
        notifications.findInbox(
            tenantId,
            recipientId,
            NotificationKind.listed(),
            unreadOnly,
            PageRequest.of(page, size));
    long unread = notifications.countUnread(tenantId, recipientId, NotificationKind.listed());

    return new MyNotices(
        found.getContent().stream().map(NoticeView::of).toList(),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        unread);
  }
}
