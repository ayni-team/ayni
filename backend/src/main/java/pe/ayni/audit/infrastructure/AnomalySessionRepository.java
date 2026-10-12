package pe.ayni.audit.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.audit.domain.model.AnomalySession;
import pe.ayni.audit.domain.model.AnomalySessionId;

/** The sessions that support each anomaly. */
public interface AnomalySessionRepository extends JpaRepository<AnomalySession, AnomalySessionId> {

  List<AnomalySession> findByTenantIdAndAnomalyId(String tenantId, UUID anomalyId);

  List<AnomalySession> findByTenantIdAndAnomalyIdIn(String tenantId, Collection<UUID> anomalyIds);

  List<AnomalySession> findByTenantIdAndSessionIdIn(String tenantId, Collection<UUID> sessionIds);
}
