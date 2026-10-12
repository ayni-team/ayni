package pe.ayni.skills.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;

/** Every method takes the university: no query here may cross into another one's tutors. */
public interface OfferedSkillRepository extends JpaRepository<OfferedSkill, UUID> {

  Optional<OfferedSkill> findByTenantIdAndTutorIdAndCatalogItemId(
      String tenantId, UUID tutorId, UUID catalogItemId);

  List<OfferedSkill> findByTenantIdAndTutorIdAndStatus(
      String tenantId, UUID tutorId, OfferedSkillStatus status);

  /** Whether some tutor of this tenant is enabled to teach the item, for {@code SkillsApi}. */
  boolean existsByTenantIdAndTutorIdAndCatalogItemIdAndStatus(
      String tenantId, UUID tutorId, UUID catalogItemId, OfferedSkillStatus status);

  /**
   * The catalogue items a tutor holds in the given status.
   *
   * <p>Written out because a derived query cannot select a single column: Spring Data ignores
   * whatever sits between {@code find} and {@code By}, returns whole entities, and Hibernate
   * refuses to hand them back as identifiers.
   */
  @Query(
      """
      select skill.catalogItemId from OfferedSkill skill
      where skill.tenantId = :tenantId
        and skill.tutorId = :tutorId
        and skill.status = :status
      """)
  List<UUID> findCatalogItemIds(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("status") OfferedSkillStatus status);

  /**
   * The catalogue items the tutor holds in any status but the given one.
   *
   * <p>Used with {@code WITHDRAWN} to find what is still taken: an item the tutor withdrew can be
   * offered again, so it is not among them.
   */
  @Query(
      """
      select skill.catalogItemId from OfferedSkill skill
      where skill.tenantId = :tenantId
        and skill.tutorId = :tutorId
        and skill.status <> :excluded
      """)
  List<UUID> findCatalogItemIdsNotIn(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("excluded") OfferedSkillStatus excluded);

  /** Everything the tutor offers or ever offered, whatever its status. */
  List<OfferedSkill> findByTenantIdAndTutorId(String tenantId, UUID tutorId);

  /**
   * The tutor's skill for an item, locked until the transaction ends.
   *
   * <p>For the submission of evidence: two submissions of the same rejected skill at once must not
   * both put it back in the queue, so the second waits and then finds it already pending.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select skill from OfferedSkill skill
      where skill.tenantId = :tenantId
        and skill.tutorId = :tutorId
        and skill.catalogItemId = :catalogItemId
      """)
  Optional<OfferedSkill> lockByTenantIdAndTutorIdAndCatalogItemId(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("catalogItemId") UUID catalogItemId);

  /**
   * One offered skill, locked until the transaction ends.
   *
   * <p>The row carries no version column, and two requests withdrawing the same skill at once would
   * both read it as enabled and both announce the withdrawal. The lock makes the second one wait,
   * and then find it already withdrawn.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select skill from OfferedSkill skill
      where skill.tenantId = :tenantId and skill.id = :id
      """)
  Optional<OfferedSkill> lockByTenantIdAndId(
      @Param("tenantId") String tenantId, @Param("id") UUID id);
}
