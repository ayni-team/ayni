package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import pe.ayni.booking.domain.model.ExceptionKind;

/** Request body for a date-specific ADD or REMOVE exception. */
@Schema(name = "AvailabilityExceptionRequest")
public record AvailabilityExceptionRequest(
    @NotNull
        @Schema(
            description = "Calendar date whose availability differs from the weekly pattern.",
            example = "2026-10-05")
    LocalDate exceptionDate,
    @Schema(
        description =
            "Start of the exception window. Required for ADD and timed REMOVE; null together with endsAtTime means a whole-day REMOVE.",
        example = "17:00:00",
        nullable = true)
    LocalTime startsAtTime,
    @Schema(
        description =
            "End of the exception window. Required for ADD and timed REMOVE; null together with startsAtTime means a whole-day REMOVE.",
        example = "18:00:00",
        nullable = true)
    LocalTime endsAtTime,
    @NotNull
        @Schema(
            description =
                "Type of exception: ADD creates extraordinary availability and REMOVE removes availability.",
            example = "REMOVE")
    ExceptionKind kind) {}
