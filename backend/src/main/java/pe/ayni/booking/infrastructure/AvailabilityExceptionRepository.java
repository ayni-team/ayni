package pe.ayni.booking.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.booking.domain.model.AvailabilityException;

/**
 * The date specific deviations from a tutor's patterns.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface AvailabilityExceptionRepository
    extends JpaRepository<AvailabilityException, UUID> {

  Optional<AvailabilityException> findByTenantIdAndId(String tenantId, UUID id);

  /** The exceptions falling on a range of dates, both ends included, for generating blocks. */
  @Query(
      """
      select exception from AvailabilityException exception
      where exception.tenantId = :tenantId
        and exception.tutorId = :tutorId
        and exception.exceptionDate between :from and :to
      order by exception.exceptionDate asc, exception.startsAtTime asc
      """)
  List<AvailabilityException> findWithinHorizon(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
