package pe.ayni.booking.application;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingView;
import pe.ayni.booking.OpenHourView;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.booking.infrastructure.HourBlockRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What the other modules see of booking.
 *
 * <p>Sessions keeps only the booking's id; the need description, which the tutor reads before the
 * session and the support material will be generated from, is read through here rather than copied
 * into every module that wants it.
 *
 * <p>Matching asks here which hours a tutor still has open, because its search is a projection fed
 * by events and an event only tells it about hours at the moment they appear.
 */
@Service
public class BookingService implements BookingApi {

  private final BookingRepository bookings;
  private final HourBlockRepository blocks;

  BookingService(BookingRepository bookings, HourBlockRepository blocks) {
    this.bookings = bookings;
    this.blocks = blocks;
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

  @Override
  @Transactional
  public boolean isConfirmed(UUID bookingId) {
    return bookings
        .lockByTenantIdAndId(TenantContext.require(), bookingId)
        .map(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
        .orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public List<OpenHourView> openHoursOf(UUID tutorId, Instant from) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");
    Objects.requireNonNull(from, "from must not be null");

    return blocks.findOpenFrom(TenantContext.require(), tutorId, from).stream()
        .map(
            block ->
                new OpenHourView(
                    block.getId(), block.getTutorId(), block.getStartsAt(), block.getEndsAt()))
        .toList();
  }
}
