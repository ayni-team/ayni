package pe.ayni.booking.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.application.dto.BookingSummaryResponse;
import pe.ayni.booking.domain.repository.BookingRepository;

import java.util.List;

/**
 * Read-only use case for retrieving upcoming bookings for a student (US04).
 */
@Service
public class GetUpcomingBookingsUseCase {

    private final BookingRepository bookingRepository;

    /**
     * Constructs use case with repository.
     *
     * @param bookingRepository booking repository instance
     */
    public GetUpcomingBookingsUseCase(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * Retrieves upcoming bookings for a given student and tenant.
     *
     * @param tenantId  tenant identifier
     * @param studentId student identifier
     * @return list of upcoming booking summaries
     */
    @Transactional(readOnly = true)
    public List<BookingSummaryResponse> execute(String tenantId, String studentId) {
        return bookingRepository.findUpcomingByStudentId(tenantId, studentId).stream()
                .map(b -> new BookingSummaryResponse(
                        b.getId(),
                        b.getTenantId(),
                        b.getTutorId(),
                        b.getStudentId(),
                        b.getCourseName(),
                        b.getScheduledAt(),
                        b.getStatus().name()
                ))
                .toList();
    }
}