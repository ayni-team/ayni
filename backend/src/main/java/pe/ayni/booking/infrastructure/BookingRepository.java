package pe.ayni.booking.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.booking.domain.model.Booking;

/**
 * The reservations.
 *
 * <p>Every method takes the university: until the database enforces the separation by itself, a
 * query without that filter reads another university's data.
 */
public interface BookingRepository extends JpaRepository<Booking, UUID> {

  Optional<Booking> findByTenantIdAndId(String tenantId, UUID id);
}
