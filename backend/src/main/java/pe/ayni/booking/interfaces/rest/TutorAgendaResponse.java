package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.application.TutorAgendaView;
import pe.ayni.booking.application.TutorBooking;
import pe.ayni.booking.interfaces.rest.StudentBookingsResponse.Party;

/** What the tutor has ahead in a period of days of the university (US21). */
@Schema(name = "TutorAgenda")
public record TutorAgendaResponse(
    @Schema(description = "First day, in the university's zone.", example = "2026-10-12")
    LocalDate from,
    @Schema(description = "Last day, included.", example = "2026-10-18") LocalDate to,
    @Schema(description = "Time zone of the university, in which the days and the local times are.",
        example = "America/Lima")
    String timezone,
    @Schema(description = "Booked sessions and free hours, in the order they come.")
    List<Line> lines) {

  /** One line of the agenda. */
  @Schema(name = "TutorAgendaLine")
  public record Line(
      @Schema(description = "BOOKED, a session somebody booked, or FREE, an hour nobody has yet.",
          example = "BOOKED")
      TutorAgendaView.Kind kind,
      @Schema(example = "2026-10-16T14:00:00Z") Instant startsAt,
      @Schema(example = "2026-10-16T15:00:00Z") Instant endsAt,
      @Schema(description = "The day, in the university's zone.", example = "2026-10-16")
      LocalDate date,
      @Schema(description = "Start, in the university's zone.", type = "string", example = "09:00:00")
      LocalTime startTime,
      @Schema(description = "End, in the university's zone.", type = "string", example = "10:00:00")
      LocalTime endTime,
      @Schema(description = "The booking, for a BOOKED line; null for a FREE one.") Booked booking) {}

  /** A session booked with the tutor. */
  @Schema(name = "TutorBooking")
  public record Booked(
      @Schema(example = "d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f") UUID bookingId,
      @Schema(example = "2026-10-16T14:00:00Z") Instant startsAt,
      @Schema(example = "2026-10-16T15:00:00Z") Instant endsAt,
      @Schema(example = "1") int hours,
      @Schema(description = "CONFIRMED, CANCELLED, COMPLETED or NO_SHOW.", example = "CONFIRMED")
      BookingStatus status,
      Party student,
      @Schema(description = "The course or skill the session is about.") Party catalogItem,
      @Schema(description = "What the student said they need help with.",
          example = "Normal forms before Friday's exam")
      String needDescription,
      @Schema(description =
          "True from fifteen minutes before the start until the end: the session can be entered"
              + " now, so the agenda highlights it.", example = "false")
      boolean joinable) {

    static Booked of(TutorBooking booking) {
      return new Booked(
          booking.bookingId(),
          booking.startsAt(),
          booking.endsAt(),
          booking.hours(),
          booking.status(),
          new Party(booking.studentId(), booking.studentName()),
          new Party(booking.catalogItemId(), booking.catalogItemName()),
          booking.needDescription(),
          booking.joinable());
    }
  }

  static TutorAgendaResponse of(TutorAgendaView agenda) {
    return new TutorAgendaResponse(
        agenda.from(),
        agenda.to(),
        agenda.timezone(),
        agenda.lines().stream()
            .map(
                line ->
                    new Line(
                        line.kind(),
                        line.startsAt(),
                        line.endsAt(),
                        line.date(),
                        line.startTime(),
                        line.endTime(),
                        line.booking() == null ? null : Booked.of(line.booking())))
            .toList());
  }
}
