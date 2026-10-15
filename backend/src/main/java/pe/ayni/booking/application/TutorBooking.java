package pe.ayni.booking.application;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.booking.BookingStatus;

/**
 * A session booked with a tutor as their agenda shows it (US21): who comes, about what, and what
 * the student said they need.
 *
 * @param joinable whether the session can be entered now: from fifteen minutes before the start
 *     until the end
 * @param studentName {@code null} when identity no longer knows the student
 * @param catalogItemName {@code null} when skills no longer knows the item
 */
public record TutorBooking(
    UUID bookingId,
    Instant startsAt,
    Instant endsAt,
    int hours,
    BookingStatus status,
    UUID studentId,
    String studentName,
    UUID catalogItemId,
    String catalogItemName,
    String needDescription,
    boolean joinable) {}
