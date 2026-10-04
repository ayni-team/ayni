package pe.ayni.booking.domain.repository;

import pe.ayni.booking.domain.model.Booking;
import java.util.List;

/**
 * Domain repository interface for managing tutoring bookings (US04 / US21).
 */
public interface BookingRepository {

    /**
     * Finds upcoming bookings for a student within a tenant.
     *
     * @param tenantId  tenant identifier
     * @param studentId student identifier
     * @return list of upcoming bookings
     */
    List<Booking> findUpcomingByStudentId(String tenantId, String studentId);

    /**
     * Finds scheduled sessions for a tutor within a tenant.
     *
     * @param tenantId tenant identifier
     * @param tutorId  tutor identifier
     * @return list of tutor's bookings
     */
    List<Booking> findByTutorId(String tenantId, String tutorId);

    /**
     * Saves a booking entity.
     *
     * @param booking entity to save
     * @return saved entity
     */
    Booking save(Booking booking);
}