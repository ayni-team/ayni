package pe.ayni.sessions.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.sessions.domain.model.Whiteboard;

/**
 * The whiteboards of the sessions, one per session. Whoever changes one locks its session first,
 * which is what gives the operations their order (ADR 0009).
 */
public interface WhiteboardRepository extends JpaRepository<Whiteboard, UUID> {

  Optional<Whiteboard> findByTenantIdAndSessionId(String tenantId, UUID sessionId);
}
