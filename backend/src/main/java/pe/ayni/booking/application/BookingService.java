package pe.ayni.booking.application;

import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingView;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What the other modules see of booking.
 *
 * <p>Sessions keeps only the booking's id; the need description, which the tutor reads before the
 * session and the support material will be generated from, is read through here rather than copied
 * into every module that wants it.
 */
@Service
public class BookingService implements BookingApi {

  private final BookingRepository bookings;

  BookingService(BookingRepository bookings) {
    this.bookings = bookings;
  }

  @Override
  @Transactional(readOnly = true)
  public BookingView requireBooking(UUID bookingId) {
    Booking booking =
        bookings
            .findByTenantIdAndId(TenantContext.require(), bookingId)
            .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));
    return new BookingView(
        booking.getId(),
        booking.getStudentId(),
        booking.getTutorId(),
        booking.getCatalogItemId(),
        booking.getStartsAt(),
        booking.getEndsAt(),
        booking.getHours(),
        booking.getNeedDescription(),
        booking.getStatus());
  }
}
