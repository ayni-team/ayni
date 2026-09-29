package pe.ayni.sessions.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
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

  /**
   * The sessions whose presence codes are due and not issued yet: in progress, at least {@code
   * Session.PRESENCE_CHECK_AFTER} past their scheduled start, and not over.
   *
   * @param startedBy the latest scheduled start that is due, now minus the delay
   */
  @Query(
      """
      select session.id from Session session
      where session.tenantId = :tenantId
        and session.status = pe.ayni.sessions.SessionStatus.IN_PROGRESS
        and session.scheduledStart <= :startedBy
        and session.scheduledEnd > :now
        and not exists (
          select 1 from PresenceCheck presence
          where presence.tenantId = :tenantId and presence.sessionId = session.id)
      order by session.scheduledStart
      """)
  List<UUID> findDueForPresenceCheck(
      @Param("tenantId") String tenantId,
      @Param("startedBy") Instant startedBy,
      @Param("now") Instant now);

  /**
   * The sessions still in progress whose booked hour ended at least {@code
   * Session.CLOSES_AFTER_END} ago, which close on their own.
   *
   * @param endedBy the latest scheduled end that is due, now minus that delay
   */
  @Query(
      """
      select session.id from Session session
      where session.tenantId = :tenantId
        and session.status = pe.ayni.sessions.SessionStatus.IN_PROGRESS
        and session.scheduledEnd <= :endedBy
      order by session.scheduledEnd
      """)
  List<UUID> findDueToClose(
      @Param("tenantId") String tenantId, @Param("endedBy") Instant endedBy);
}
