package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
    Instant createdAt) {

  static AvailabilityPauseResponse of(AvailabilityPause pause) {
    return new AvailabilityPauseResponse(
        pause.getId(),
        pause.getTutorId(),
        pause.getStartsOn(),
        pause.getEndsOn(),
        pause.getCreatedAt());
  }
}
