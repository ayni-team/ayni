package pe.ayni.booking.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pe.ayni.booking.domain.model.AvailabilityPattern;

import java.time.LocalDate;
import java.time.LocalTime;
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

    @Query("""
    SELECT p FROM AvailabilityPattern p
    WHERE p.tenantId = :tenantId
      AND p.tutorId = :tutorId
      AND p.dayOfWeek = :dayOfWeek
      AND (:patternId IS NULL OR p.id <> :patternId)
      AND (p.validUntil IS NULL OR :validFrom IS NULL OR p.validUntil >= :validFrom)
      AND (:validUntil IS NULL OR p.validFrom <= :validUntil)
      AND p.startsAtTime < :endsAtTime
      AND p.endsAtTime > :startsAtTime
""")
    List<AvailabilityPattern> findOverlappingPatterns(
            @Param("tenantId") String tenantId,
            @Param("tutorId") UUID tutorId,
            @Param("dayOfWeek") short dayOfWeek,
            @Param("patternId") UUID patternId, // To ignore the pattern itself while editing
            @Param("validFrom") LocalDate validFrom,
            @Param("validUntil") LocalDate validUntil,
            @Param("startsAtTime") LocalTime startsAtTime,
            @Param("endsAtTime") LocalTime endsAtTime
    );
}