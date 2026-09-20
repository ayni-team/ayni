package pe.ayni.booking.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.booking.domain.model.AvailabilityPause;

/**
 * The periods a tutor is away.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface AvailabilityPauseRepository extends JpaRepository<AvailabilityPause, UUID> {

  Optional<AvailabilityPause> findByTenantIdAndId(String tenantId, UUID id);

  /** The pauses touching a range of dates, both ends included, for generating blocks. */
  @Query(
      """
      select pause from AvailabilityPause pause
      where pause.tenantId = :tenantId
        and pause.tutorId = :tutorId
        and pause.startsOn <= :to
        and pause.endsOn >= :from
      order by pause.startsOn
      """)
  List<AvailabilityPause> findOverlappingPauses(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
