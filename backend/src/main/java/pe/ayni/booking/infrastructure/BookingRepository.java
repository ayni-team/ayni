package pe.ayni.booking.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.ayni.booking.domain.model.Booking;

/**
 * The reservations.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface BookingRepository extends JpaRepository<Booking, UUID> {

  Optional<Booking> findByTenantIdAndId(String tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select booking from Booking booking "
          + "where booking.tenantId = :tenantId and booking.id = :id")
  Optional<Booking> lockByTenantIdAndId(
      @Param("tenantId") String tenantId, @Param("id") UUID id);
}
