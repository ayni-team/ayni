package pe.ayni.booking.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.booking.domain.model.Booking;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Booking entities.
 */
public interface SpringDataBookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByTenantIdAndStudentId(String tenantId, String studentId);

    List<Booking> findByTenantIdAndTutorId(String tenantId, String tutorId);
}