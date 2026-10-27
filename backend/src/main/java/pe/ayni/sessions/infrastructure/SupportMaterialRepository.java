package pe.ayni.sessions.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.sessions.domain.model.SupportMaterial;

/** The support material of the sessions, one per session at most (US07). */
public interface SupportMaterialRepository extends JpaRepository<SupportMaterial, UUID> {

  Optional<SupportMaterial> findByTenantIdAndSessionId(String tenantId, UUID sessionId);

  boolean existsByTenantIdAndSessionId(String tenantId, UUID sessionId);
}
