package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.application.ConfirmedBooking;

/** A confirmed booking. */
@Schema(name = "Booking")
public record BookingResponse(
    @Schema(description = "Unique identifier of the booking.",
        example = "d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f")
    UUID id,
    @Schema(description = "Student who booked.", example = "11111111-1111-4111-8111-111111111111")
    UUID studentId,
    @Schema(description = "Tutor booked.", example = "22222222-2222-4222-8222-222222222222")
    UUID tutorId,
    @Schema(description = "Catalogue item (skill) the session is about.",
        example = "b0000000-0000-4000-8000-000000000102")
    UUID catalogItemId,
    @Schema(description = "Start of the first hour, UTC.", example = "2026-09-29T23:00:00Z")
    Instant startsAt,
    @Schema(description = "End of the last hour, UTC.", example = "2026-09-30T01:00:00Z")
    Instant endsAt,
    @Schema(description = "Consecutive hours booked.", example = "2")
    int hours,
    @Schema(description = "Credits deducted from the student's balance: one per hour.",
        example = "2")
    int creditsCharged,
    @Schema(description = "What the student needs help with.",
        example = "Normal forms and how to decompose a table before Friday's exam")
    String needDescription,
    @Schema(description = "Always CONFIRMED for a booking just made.", example = "CONFIRMED")
    BookingStatus status,
    @Schema(description = "The one-hour blocks the booking took, in order.",
        example = "[\"c0000000-0000-4000-8000-000000000001\"]")
    List<UUID> blockIds) {

  static BookingResponse of(ConfirmedBooking booking) {
    return new BookingResponse(
        booking.id(),
        booking.studentId(),
        booking.tutorId(),
        booking.catalogItemId(),
        booking.startsAt(),
        booking.endsAt(),
        booking.hours(),
        booking.creditsCharged(),
        booking.needDescription(),
        booking.status(),
        booking.blockIds());
  }
}
