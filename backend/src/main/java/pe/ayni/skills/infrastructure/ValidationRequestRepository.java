package pe.ayni.skills.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.skills.domain.model.ValidationRequest;

/** Every method takes the university: a coordinator never reads another one's queue. */
public interface ValidationRequestRepository extends JpaRepository<ValidationRequest, UUID> {

  Optional<ValidationRequest> findByTenantIdAndId(String tenantId, UUID id);

  /** Every submission made for the given skills, to show where each one stands. */
  List<ValidationRequest> findByTenantIdAndOfferedSkillIdIn(
      String tenantId, Collection<UUID> offeredSkillIds);
}
