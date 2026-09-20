package pe.ayni.booking.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.booking.domain.model.HourBlock;

/**
 * The generated hours.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface HourBlockRepository extends JpaRepository<HourBlock, UUID> {

  Optional<HourBlock> findByTenantIdAndId(String tenantId, UUID id);

  /**
   * A tutor's hours in a window, from the first moment included to the first moment excluded.
   *
   * <p>Half open rather than {@code between}, which includes both ends: two windows laid end to end
   * would otherwise both claim the hour on the boundary, and an agenda would show it twice.
   */
  @Query(
      """
      select block from HourBlock block
      where block.tenantId = :tenantId
        and block.tutorId = :tutorId
        and block.startsAt >= :from
        and block.startsAt < :to
      order by block.startsAt
      """)
  List<HourBlock> findWithin(
      @Param("tenantId") String tenantId,
      @Param("tutorId") UUID tutorId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
