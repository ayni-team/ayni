package pe.ayni.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.ayni.booking.domain.model.HourBlock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HourBlockRepository extends JpaRepository<HourBlock, UUID> {

    Optional<HourBlock> findByTenantIdAndId(String tenantId, UUID id);

    List<HourBlock> findByTenantIdAndTutorIdAndStartsAtBetween(
            String tenantId,
            UUID tutorId,
            Instant from,
            Instant to
    );
}
