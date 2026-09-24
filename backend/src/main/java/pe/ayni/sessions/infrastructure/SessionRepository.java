package pe.ayni.sessions.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.sessions.domain.model.Session;

/**
 * The sessions.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface SessionRepository extends JpaRepository<Session, UUID> {

  boolean existsByTenantIdAndBookingId(String tenantId, UUID bookingId);

  Optional<Session> findByTenantIdAndBookingId(String tenantId, UUID bookingId);
}
