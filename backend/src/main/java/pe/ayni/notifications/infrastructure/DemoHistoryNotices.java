package pe.ayni.notifications.infrastructure;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.ayni.notifications.application.ListMyNotificationsQuery;
import pe.ayni.notifications.application.MarkNotificationReadUseCase;
import pe.ayni.notifications.application.NoticeView;
import pe.ayni.shared.demo.DemoNotices;

/**
 * Notifications' part of the history of the {@code demo} profile: a person who has opened some of
 * their notices, with the use cases of the notices screen.
 */
@Component
@Profile("demo")
class DemoHistoryNotices implements DemoNotices {

  private final ListMyNotificationsQuery list;
  private final MarkNotificationReadUseCase markRead;

  DemoHistoryNotices(ListMyNotificationsQuery list, MarkNotificationReadUseCase markRead) {
    this.list = list;
    this.markRead = markRead;
  }

  @Override
  public int markOldestRead(UUID recipientId, int count) {
    List<NoticeView> oldest =
        list.execute(recipientId, true, 0, 100).items().stream()
            .sorted(Comparator.comparing(NoticeView::createdAt))
            .limit(count)
            .toList();
    oldest.forEach(notice -> markRead.execute(recipientId, notice.id()));
    return oldest.size();
  }
}
