package pe.ayni.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.ayni.booking.domain.model.AvailabilityException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, UUID> {

    Optional<AvailabilityException> findByTenantIdAndId(String tenantId, UUID id);

    @Query("""
        SELECT e FROM AvailabilityException e
        WHERE e.tenantId = :tenantId
          AND e.tutorId = :tutorId
          AND e.exceptionDate BETWEEN :from AND :to
        ORDER BY e.exceptionDate ASC, e.startsAtTime ASC
    """)
    List<AvailabilityException> findWithinHorizon(
            @Param("tenantId") String tenantId,
            @Param("tutorId") UUID tutorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
