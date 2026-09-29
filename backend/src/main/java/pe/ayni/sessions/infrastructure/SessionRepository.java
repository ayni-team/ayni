package pe.ayni.sessions.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.Session;

/**
 * The sessions.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface SessionRepository extends JpaRepository<Session, UUID> {

  boolean existsByTenantIdAndBookingId(String tenantId, UUID bookingId);

  Optional<Session> findByTenantIdAndBookingId(String tenantId, UUID bookingId);

  Optional<Session> findByTenantIdAndId(String tenantId, UUID id);

  /** A tutor's sessions in one state, the earliest scheduled first. */
  List<Session> findByTenantIdAndTutorIdAndStatusOrderByScheduledStartAsc(
      String tenantId, UUID tutorId, SessionStatus status);
}
