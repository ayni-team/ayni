package pe.ayni.sessions.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  /**
   * A session, locked until the transaction ends.
   *
   * <p>For joining: the student and the tutor usually arrive within seconds of each other, and both
   * could otherwise read the session as not started and both start it. The lock makes the second
   * one wait and find it in progress.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select session from Session session where session.tenantId = :tenantId and session.id = :id")
  Optional<Session> lockByTenantIdAndId(@Param("tenantId") String tenantId, @Param("id") UUID id);

  /** A tutor's sessions in one state, the earliest scheduled first. */
  List<Session> findByTenantIdAndTutorIdAndStatusOrderByScheduledStartAsc(
      String tenantId, UUID tutorId, SessionStatus status);
}
