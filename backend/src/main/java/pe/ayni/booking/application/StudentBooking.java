package pe.ayni.booking.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.booking.BookingStatus;

/**
 * One of a student's bookings as their list shows it (US04).
 *
 * @param joinable whether the session can be entered now: from fifteen minutes before the start
 *     until the end
 * @param tutorName {@code null} when identity no longer knows the tutor
 * @param catalogItemName {@code null} when skills no longer knows the item
 */
public record StudentBooking(
    UUID bookingId,
    Instant startsAt,
    Instant endsAt,
    int hours,
    BookingStatus status,
    UUID tutorId,
    String tutorName,
    UUID catalogItemId,
    String catalogItemName,
    boolean joinable) {}
