package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/** Request body for a recurring weekly availability range. */
@Schema(name = "DeclareAvailabilityRequest")
public record DeclareAvailabilityRequest(
    @NotNull
        @Schema(
            description = "Day of the week when the recurring availability starts.",
            example = "MONDAY")
    DayOfWeek dayOfWeek,
    @NotNull
        @Schema(
            description = "Local time when the tutor becomes available. The range uses one-hour blocks.",
            example = "17:00:00")
    LocalTime startsAtTime,
    @NotNull
        @Schema(
            description = "Local time when the tutor stops being available. It must be after startsAtTime.",
            example = "19:00:00")
    LocalTime endsAtTime,
    @NotNull
        @Schema(
            description = "First calendar date on which this weekly pattern is valid.",
            example = "2026-09-28")
    LocalDate validFrom,
    @Schema(
        description =
            "Last calendar date on which this weekly pattern is valid. Null means that the pattern has no end date.",
        example = "2026-12-18",
        nullable = true)
    LocalDate validUntil) {}
