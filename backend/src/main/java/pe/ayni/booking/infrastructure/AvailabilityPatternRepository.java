package pe.ayni.booking.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.booking.domain.model.AvailabilityPattern;

/**
 * A tutor's recurring weekly windows.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface AvailabilityPatternRepository extends JpaRepository<AvailabilityPattern, UUID> {

  Optional<AvailabilityPattern> findByTenantIdAndId(String tenantId, UUID id);

  List<AvailabilityPattern> findByTenantIdAndTutorId(String tenantId, UUID tutorId);

  /** The patterns still valid somewhere inside a range of dates, for generating blocks. */
  @Query(
      """
      select pattern from AvailabilityPattern pattern
      where pattern.tenantId = :tenantId
        and pattern.tutorId = :tutorId
        and pattern.validFrom <= :horizonEnd
        and (pattern.validUntil is null or pattern.validUntil >= :horizonStart)
      """)
  List<AvailabilityPattern> findActiveInHorizon(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("horizonStart") LocalDate horizonStart,
      @Param("horizonEnd") LocalDate horizonEnd);

  /**
   * Every window this tutor already has on a weekday, which is where a collision could be.
   *
   * <p>Deliberately no narrower than that. Whether two windows actually collide is {@link
   * AvailabilityPattern#overlaps}, where the rule can be read and tested without a database: it was
   * written here in SQL as well, and two copies of one rule is one copy too many. A tutor has a
   * handful of windows per weekday, so narrowing further would buy nothing anyway.
   *
   * <p>A pattern being edited comes back in this list too. The caller drops it, because only the
   * caller knows which one it is holding.
   *
   * @param dayOfWeek 1 for Monday, as the column stores it
   */
  List<AvailabilityPattern> findByTenantIdAndTutorIdAndDayOfWeek(
      String tenantId, UUID tutorId, short dayOfWeek);
}
