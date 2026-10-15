package pe.ayni.recognition.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.recognition.domain.model.RequestedSession;

/**
 * The sessions presented with each request.
 *
 * <p>Every method takes the university: the policy of row level security is the net, the filter is
 * the query.
 */
public interface RequestedSessionRepository
    extends JpaRepository<RequestedSession, RequestedSession.Key> {

  /** The sessions of a request, in the order they were taught, released ones included. */
  List<RequestedSession> findByTenantIdAndRequestIdOrderByStartedAtAsc(
      String tenantId, UUID requestId);

  /** The sessions of these requests, in the order they were taught, released ones included. */
  List<RequestedSession> findByTenantIdAndRequestIdInOrderByStartedAtAsc(
      String tenantId, Collection<UUID> requestIds);

  /** One session as it was presented with one request. */
  Optional<RequestedSession> findByTenantIdAndRequestIdAndSessionId(
      String tenantId, UUID requestId, UUID sessionId);

  /**
   * Which of these sessions back a request of the university now. A session of a rejected request
   * was released and backs nothing, so it is free again.
   */
  @Query(
      """
      select requested.sessionId from RequestedSession requested
      where requested.tenantId = :tenantId
        and requested.sessionId in :sessionIds
        and requested.releasedAt is null
      """)
  List<UUID> findClaimed(
      @Param("tenantId") String tenantId, @Param("sessionIds") Collection<UUID> sessionIds);
}
