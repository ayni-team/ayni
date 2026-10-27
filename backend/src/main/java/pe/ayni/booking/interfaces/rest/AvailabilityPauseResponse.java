package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import pe.ayni.booking.application.DeclaredPause;
import pe.ayni.booking.application.HoursWithdrawal;
import pe.ayni.booking.domain.model.AvailabilityPause;

/** The inclusive availability pause that was created. */
@Schema(name = "AvailabilityPause")
public record AvailabilityPauseResponse(
    @Schema(description = "Unique identifier of the availability pause.",
        example = "a0000000-0000-4000-8000-000000000003")
    UUID id,
    @Schema(description = "Identifier of the tutor whose availability is paused.",
        example = "11111111-1111-4111-8111-111111111111")
    UUID tutorId,
    @Schema(description = "First calendar date included in the pause.",
        example = "2026-10-12")
    LocalDate startsOn,
    @Schema(description = "Last calendar date included in the pause.",
        example = "2026-10-18")
    LocalDate endsOn,
    @Schema(description = "UTC timestamp when the pause was created.",
        example = "2026-09-23T21:00:00Z")
    Instant createdAt,
    @Schema(description = "Hours of the pause that already existed, free or held, taken out of "
        + "circulation and out of the search.", example = "9")
    int withdrawnHours,
    @Schema(description = "Hours of the pause that are booked. They stand: pausing does not "
        + "cancel a booking.", example = "1")
    int bookedHoursKept,
    @Schema(description = "What the tutor should know about the hours, or null when nothing.",
        example = "1 booked hour in this period stands: changing availability does not cancel bookings.",
        nullable = true)
    String notice) {

  static AvailabilityPauseResponse of(DeclaredPause declared) {
    AvailabilityPause pause = declared.pause();
    HoursWithdrawal withdrawal = declared.hours().withdrawal();
    return new AvailabilityPauseResponse(
        pause.getId(),
        pause.getTutorId(),
        pause.getStartsOn(),
        pause.getEndsOn(),
        pause.getCreatedAt(),
        withdrawal.withdrawn(),
        withdrawal.bookedKept(),
        HoursNotice.of(withdrawal));
  }
}
