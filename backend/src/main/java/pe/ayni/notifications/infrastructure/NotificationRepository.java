package pe.ayni.notifications.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.notifications.domain.model.Notification;

/**
 * The notices and how their delivery went.
 *
 * <p>Only saved and found by id today: a notice is written and marked by the same flow that
 * created it. Reading a person's own notices, which will filter by the university, arrives with
 * {@code GET /api/v1/notifications}.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {}
