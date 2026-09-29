package pe.ayni.sessions.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.sessions.domain.model.Participation;

/**
 * Who entered each session.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface ParticipationRepository extends JpaRepository<Participation, UUID> {

  Optional<Participation> findByTenantIdAndSessionIdAndUserId(
      String tenantId, UUID sessionId, UUID userId);
}
