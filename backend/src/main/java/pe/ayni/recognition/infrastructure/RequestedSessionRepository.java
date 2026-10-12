package pe.ayni.recognition.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.recognition.domain.model.RequestedSession;

public interface RequestedSessionRepository extends JpaRepository<RequestedSession, UUID> {

  /** The sessions of a request, in the order they were taught. */
  List<RequestedSession> findByRequestIdOrderByStartedAtAsc(UUID requestId);

  /** The sessions of these requests, in the order they were taught. */
  List<RequestedSession> findByRequestIdInOrderByStartedAtAsc(Collection<UUID> requestIds);

  /** Which of these sessions already back a request of the university. */
  @Query(
      """
      select requested.sessionId from RequestedSession requested
      where requested.tenantId = :tenantId and requested.sessionId in :sessionIds
      """)
  List<UUID> findClaimed(
      @Param("tenantId") String tenantId, @Param("sessionIds") Collection<UUID> sessionIds);
}
