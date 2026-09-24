package pe.ayni.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.identity.domain.model.UserSession;

public interface UserSessionRepository
        extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTenantIdAndTokenHash(
            String tenantId,
            String tokenHash);
}