package pe.ayni.skills.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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

  List<UUID> findCatalogItemIdByTenantIdAndTutorIdAndStatus(
      String tenantId, UUID tutorId, OfferedSkillStatus status);
}
