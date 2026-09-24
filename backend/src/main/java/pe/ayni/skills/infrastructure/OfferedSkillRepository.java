package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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

  /** Every catalogue item the tutor ever offered, whatever became of the offer. */
  @Query(
      """
      select skill.catalogItemId from OfferedSkill skill
      where skill.tenantId = :tenantId
        and skill.tutorId = :tutorId
      """)
  List<UUID> findAllCatalogItemIds(
      @Param("tenantId") String tenantId, @Param("tutorId") UUID tutorId);
}
