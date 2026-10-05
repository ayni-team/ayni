package pe.ayni.reputation.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.reputation.domain.model.RatingWindow;
import pe.ayni.reputation.domain.model.RatingWindowId;

public interface RatingWindowRepository
        extends JpaRepository<RatingWindow, RatingWindowId> {

    Optional<RatingWindow> findByTenantIdAndSessionId(
            String tenantId,
            UUID sessionId);
}