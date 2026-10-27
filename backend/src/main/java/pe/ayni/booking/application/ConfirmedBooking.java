package pe.ayni.booking.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.domain.model.Booking;

/** A booking just confirmed: what it covers, what it cost, and the hours it took. */
public record ConfirmedBooking(
    UUID id,
    UUID studentId,
    UUID tutorId,
    UUID catalogItemId,
    Instant startsAt,
    Instant endsAt,
    int hours,
    int creditsCharged,
    String needDescription,
    BookingStatus status,
    List<UUID> blockIds) {

  static ConfirmedBooking of(Booking booking, List<UUID> blockIds) {
    return new ConfirmedBooking(
        booking.getId(),
        booking.getStudentId(),
        booking.getTutorId(),
        booking.getCatalogItemId(),
        booking.getStartsAt(),
        booking.getEndsAt(),
        booking.getHours(),
        booking.getCreditsCharged(),
        booking.getNeedDescription(),
        booking.getStatus(),
        List.copyOf(blockIds));
  }
}
