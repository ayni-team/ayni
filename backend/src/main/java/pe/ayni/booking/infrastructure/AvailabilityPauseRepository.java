package pe.ayni.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.ayni.booking.domain.model.AvailabilityPause;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvailabilityPauseRepository extends JpaRepository<AvailabilityPause, UUID> {

    Optional<AvailabilityPause> findByTenantIdAndId(String tenantId, UUID id);

    @Query("""
        SELECT p FROM AvailabilityPause p
        WHERE p.tenantId = :tenantId
          AND p.tutorId = :tutorId
          AND p.startsOn <= :to
          AND p.endsOn >= :from
        ORDER BY p.startsOn ASC
    """)
    List<AvailabilityPause> findOverlappingPauses(
            @Param("tenantId") String tenantId,
            @Param("tutorId") UUID tutorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
