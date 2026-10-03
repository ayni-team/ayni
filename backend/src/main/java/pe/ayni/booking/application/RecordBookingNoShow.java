package pe.ayni.booking.application;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;
import pe.ayni.booking.infrastructure.BookingRepository;
import pe.ayni.shared.tenancy.TenantContext;

/** Marks a confirmed booking as a no-show when sessions reports an abandoned session. */
@Service
class RecordBookingNoShow {

  private final BookingRepository bookings;
  private final Clock clock;

  RecordBookingNoShow(BookingRepository bookings, Clock clock) {
    this.bookings = bookings;
    this.clock = clock;
  }

  @Transactional
  void record(UUID bookingId) {
    String tenantId = TenantContext.require();
    Booking booking =
        bookings
            .lockByTenantIdAndId(tenantId, bookingId)
            .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));
    if (booking.getStatus() == BookingStatus.CONFIRMED
        || booking.getStatus() == BookingStatus.NO_SHOW) {
      booking.markNoShow(clock.instant());
    }
  }
}
