package pe.ayni.booking.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.domain.repository.BookingRepository;

import java.util.List;

/**
 * Adapter implementation of BookingRepository using Spring Data JPA.
 */
@Repository
public class BookingRepositoryImpl implements BookingRepository {

    private final SpringDataBookingRepository springDataRepository;

    /**
     * Constructs implementation with Spring Data repository.
     *
     * @param springDataRepository Spring Data repository instance
     */
    public BookingRepositoryImpl(SpringDataBookingRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public List<Booking> findUpcomingByStudentId(String tenantId, String studentId) {
        return springDataRepository.findByTenantIdAndStudentId(tenantId, studentId);
    }

    @Override
    public List<Booking> findByTutorId(String tenantId, String tutorId) {
        return springDataRepository.findByTenantIdAndTutorId(tenantId, tutorId);
    }

    @Override
    public Booking save(Booking booking) {
        return springDataRepository.save(booking);
    }
}