package pe.ayni.sessions.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.sessions.domain.model.PresenceCheck;

/**
 * The presence codes issued in each session.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface PresenceCheckRepository extends JpaRepository<PresenceCheck, UUID> {

  boolean existsByTenantIdAndSessionId(String tenantId, UUID sessionId);

  Optional<PresenceCheck> findByTenantIdAndSessionIdAndUserId(
      String tenantId, UUID sessionId, UUID userId);

  /**
   * A participant's code, locked until the transaction ends.
   *
   * <p>For typing it in: without the lock, several wrong codes sent at once would each read the same
   * count of attempts and the cap could be passed by guessing in parallel.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select presence from PresenceCheck presence
      where presence.tenantId = :tenantId
        and presence.sessionId = :sessionId
        and presence.userId = :userId
      """)
  Optional<PresenceCheck> lockByTenantIdAndSessionIdAndUserId(
      @Param("tenantId") String tenantId,
      @Param("sessionId") UUID sessionId,
      @Param("userId") UUID userId);
}
