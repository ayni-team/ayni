package pe.ayni.skills.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.skills.domain.model.ValidationRequest;

/** Every method takes the university: a coordinator never reads another one's queue. */
public interface ValidationRequestRepository extends JpaRepository<ValidationRequest, UUID> {

  Optional<ValidationRequest> findByTenantIdAndId(String tenantId, UUID id);
}
