package pe.ayni.skills.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.skills.domain.model.TaughtSession;

/**
 * The sessions taught on each item.
 *
 * <p>The count takes no university on purpose: a moderator reviews an item by its use everywhere,
 * and a global tool is taught in every university. It is a number, never a row about a person.
 */
public interface TaughtSessionRepository extends JpaRepository<TaughtSession, UUID> {

  /** How many sessions were taught on the item, in every university. */
  long countByCatalogItemId(UUID catalogItemId);

  /**
   * Records that the session was taught on the item, unless it already is.
   *
   * <p>An announcement can reach a listener more than once, and the count must not grow with it.
   * The insert that finds the row already there does nothing, so two deliveries at the same moment
   * are settled by the database and not by a read followed by a write.
   *
   * @return 1 when the session was recorded, 0 when it already was
   */
  @Transactional
  @Modifying
  @Query(
      value =
          """
          insert into skills.taught_sessions (session_id, tenant_id, catalog_item_id, tutor_id, completed_at)
          values (:sessionId, :tenantId, :catalogItemId, :tutorId, :completedAt)
          on conflict (session_id) do nothing
          """,
      nativeQuery = true)
  int recordIfNew(
      @Param("sessionId") UUID sessionId,
      @Param("tenantId") String tenantId,
      @Param("catalogItemId") UUID catalogItemId,
      @Param("tutorId") UUID tutorId,
      @Param("completedAt") Instant completedAt);
}
