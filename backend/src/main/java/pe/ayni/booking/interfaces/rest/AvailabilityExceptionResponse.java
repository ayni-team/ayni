package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import pe.ayni.booking.domain.model.AvailabilityException;
import pe.ayni.booking.domain.model.ExceptionKind;

/** The date-specific availability exception that was created. */
@Schema(name = "AvailabilityException")
public record AvailabilityExceptionResponse(
    @Schema(description = "Unique identifier of the date-specific exception.",
        example = "a0000000-0000-4000-8000-000000000002")
    UUID id,
    @Schema(description = "Identifier of the tutor who owns this exception.",
        example = "11111111-1111-4111-8111-111111111111")
    UUID tutorId,
    @Schema(description = "Calendar date affected by the exception.",
        example = "2026-10-05")
    LocalDate exceptionDate,
    @Schema(description = "Start of the affected time window, or null for a whole-day REMOVE.",
        example = "17:00:00", nullable = true)
    LocalTime startsAtTime,
    @Schema(description = "End of the affected time window, or null for a whole-day REMOVE.",
        example = "18:00:00", nullable = true)
    LocalTime endsAtTime,
    @Schema(description = "Whether the exception adds or removes availability.",
        example = "REMOVE")
    ExceptionKind kind,
    @Schema(description = "UTC timestamp when the exception was created.",
        example = "2026-09-23T21:00:00Z")
    Instant createdAt) {

  static AvailabilityExceptionResponse of(AvailabilityException exception) {
    return new AvailabilityExceptionResponse(
        exception.getId(),
        exception.getTutorId(),
        exception.getExceptionDate(),
        exception.getStartsAtTime(),
        exception.getEndsAtTime(),
        exception.getKind(),
        exception.getCreatedAt());
  }
}
