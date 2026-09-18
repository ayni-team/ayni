package pe.ayni.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.ayni.booking.domain.model.AvailabilityPattern;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvailabilityPatternRepository extends JpaRepository<AvailabilityPattern, UUID> {

    Optional<AvailabilityPattern> findByTenantIdAndId(String tenantId, UUID id);

    List<AvailabilityPattern> findByTenantIdAndTutorId(String tenantId, UUID tutorId);

    @Query("""
        SELECT p FROM AvailabilityPattern p
        WHERE p.tenantId = :tenantId
          AND p.tutorId = :tutorId
          AND p.validFrom <= :horizonEnd
          AND (p.validUntil IS NULL OR p.validUntil >= :horizonStart)
    """)
    List<AvailabilityPattern> findActiveInHorizon(
            @Param("tenantId") String tenantId,
            @Param("tutorId") UUID tutorId,
            @Param("horizonStart") LocalDate horizonStart,
            @Param("horizonEnd") LocalDate horizonEnd
    );
}