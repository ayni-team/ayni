package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import pe.ayni.booking.domain.model.AvailabilityPattern;

/** The recurring weekly availability that was created. */
@Schema(name = "AvailabilityPattern")
public record AvailabilityPatternResponse(
    @Schema(description = "Unique identifier of the weekly availability pattern.",
        example = "a0000000-0000-4000-8000-000000000001")
    UUID id,
    @Schema(description = "Identifier of the tutor who owns this availability pattern.",
        example = "11111111-1111-4111-8111-111111111111")
    UUID tutorId,
    @Schema(description = "Recurring day of the week for this availability pattern.",
        example = "MONDAY")
    DayOfWeek dayOfWeek,
    @Schema(description = "Local time when the tutor becomes available.",
        example = "17:00:00")
    LocalTime startsAtTime,
    @Schema(description = "Local time when the tutor stops being available.",
        example = "19:00:00")
    LocalTime endsAtTime,
    @Schema(description = "First date on which the pattern is valid.",
        example = "2026-09-28")
    LocalDate validFrom,
    @Schema(description = "Last date on which the pattern is valid; null means no end date.",
        example = "2026-12-18", nullable = true)
    LocalDate validUntil,
    @Schema(description = "UTC timestamp when the pattern was created.",
        example = "2026-09-23T21:00:00Z")
    Instant createdAt) {

  static AvailabilityPatternResponse of(AvailabilityPattern pattern) {
    return new AvailabilityPatternResponse(
        pattern.getId(),
        pattern.getTutorId(),
        pattern.getDayOfWeek(),
        pattern.getStartsAtTime(),
        pattern.getEndsAtTime(),
        pattern.getValidFrom(),
        pattern.getValidUntil(),
        pattern.getCreatedAt());
  }
}
