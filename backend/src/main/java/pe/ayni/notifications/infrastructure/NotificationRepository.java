package pe.ayni.notifications.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.notifications.domain.model.Notification;
import pe.ayni.notifications.domain.model.NotificationKind;

/**
 * The notices and how their delivery went.
 *
 * <p>The delivery flows save a notice and mark it by id. The person's own notices are read by the
 * university and the recipient together, and only of the kinds the application lists: the caller
 * passes those, so the repository does not decide which kinds are secrets.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  /**
   * A page of somebody's notices, newest first. The identifier breaks ties between notices of the
   * same instant so a page boundary never repeats one.
   */
  @Query(
      """
      select n from Notification n
      where n.tenantId = :tenantId
        and n.recipientId = :recipientId
        and n.kind in :kinds
        and (:unreadOnly = false or n.readAt is null)
      order by n.createdAt desc, n.id desc
      """)
  Page<Notification> findInbox(
      @Param("tenantId") String tenantId,
      @Param("recipientId") UUID recipientId,
      @Param("kinds") Collection<NotificationKind> kinds,
      @Param("unreadOnly") boolean unreadOnly,
      Pageable pageable);

  @Query(
      """
      select count(n) from Notification n
      where n.tenantId = :tenantId
        and n.recipientId = :recipientId
        and n.kind in :kinds
        and n.readAt is null
      """)
  long countUnread(
      @Param("tenantId") String tenantId,
      @Param("recipientId") UUID recipientId,
      @Param("kinds") Collection<NotificationKind> kinds);

  /**
   * One of somebody's notices, locked until the transaction ends: two requests marking it read
   * at once take turns, and the second finds the date the first set.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select n from Notification n
      where n.tenantId = :tenantId
        and n.recipientId = :recipientId
        and n.kind in :kinds
        and n.id = :id
      """)
  Optional<Notification> lockOwned(
      @Param("tenantId") String tenantId,
      @Param("recipientId") UUID recipientId,
      @Param("kinds") Collection<NotificationKind> kinds,
      @Param("id") UUID id);

  /** Every unread notice of somebody, locked in a fixed order so two requests cannot deadlock. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select n from Notification n
      where n.tenantId = :tenantId
        and n.recipientId = :recipientId
        and n.kind in :kinds
        and n.readAt is null
      order by n.id
      """)
  List<Notification> lockUnread(
      @Param("tenantId") String tenantId,
      @Param("recipientId") UUID recipientId,
      @Param("kinds") Collection<NotificationKind> kinds);
}
