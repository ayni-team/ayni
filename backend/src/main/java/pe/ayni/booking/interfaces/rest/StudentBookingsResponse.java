package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.application.BookingsPage;
import pe.ayni.booking.application.StudentBooking;
import pe.ayni.booking.domain.model.BookingTimeline;

/** A page of the student's tutorings (US04). */
@Schema(name = "StudentBookings")
public record StudentBookingsResponse(
    @Schema(description = "UPCOMING, the nearest first, or HISTORY, the most recent first.",
        example = "UPCOMING")
    BookingTimeline view,
    @Schema(description = "Time zone of the university, to show the UTC instants in.",
        example = "America/Lima")
    String timezone,
    @Schema(description = "Page number, from 0.", example = "0") int page,
    @Schema(description = "Page size.", example = "20") int size,
    @Schema(description = "Bookings in this half in all.", example = "3") long total,
    List<Item> items) {

  /** One tutoring. */
  @Schema(name = "StudentBooking")
  public record Item(
      @Schema(example = "d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f") UUID bookingId,
      @Schema(description = "Start of the first hour, UTC.", example = "2026-10-16T14:00:00Z")
      Instant startsAt,
      @Schema(description = "End of the last hour, UTC.", example = "2026-10-16T15:00:00Z")
      Instant endsAt,
      @Schema(example = "1") int hours,
      @Schema(description = "CONFIRMED, CANCELLED, COMPLETED or NO_SHOW.", example = "CONFIRMED")
      BookingStatus status,
      Party tutor,
      @Schema(description = "The course or skill the tutoring is about.") Party catalogItem,
      @Schema(description =
          "True from fifteen minutes before the start until the end: the session can be entered"
              + " now, so the list highlights it.", example = "false")
      boolean joinable) {}

  /** Somebody or something a booking names, with the name another module keeps. */
  @Schema(name = "BookingParty")
  public record Party(
      @Schema(example = "22222222-2222-4222-8222-222222222222") UUID id,
      @Schema(description = "Null when it is no longer known.", example = "Bruno Salas")
      String name) {}

  static StudentBookingsResponse of(BookingsPage<StudentBooking> page) {
    return new StudentBookingsResponse(
        page.view(),
        page.timezone(),
        page.page(),
        page.size(),
        page.total(),
        page.items().stream()
            .map(
                booking ->
                    new Item(
                        booking.bookingId(),
                        booking.startsAt(),
                        booking.endsAt(),
                        booking.hours(),
                        booking.status(),
                        new Party(booking.tutorId(), booking.tutorName()),
                        new Party(booking.catalogItemId(), booking.catalogItemName()),
                        booking.joinable()))
            .toList());
  }
}
