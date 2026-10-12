package pe.ayni.reputation.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.reputation.domain.model.Rating;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    boolean existsByTenantIdAndSessionIdAndDirection(
            String tenantId,
            UUID sessionId,
            Rating.Direction direction);
}