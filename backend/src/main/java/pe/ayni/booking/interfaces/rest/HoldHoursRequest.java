package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** The hours a student chose, to take them out of circulation while they confirm. */
@Schema(name = "HoldHoursRequest")
public record HoldHoursRequest(
    @NotNull
        @Schema(
            description = "Tutor whose hours are held.",
            example = "22222222-2222-4222-8222-222222222222")
    UUID tutorId,
    @NotNull
        @Schema(
            description = "Start of the first hour, in ISO 8601 UTC, exactly as the search returns it.",
            example = "2026-09-29T23:00:00Z")
    Instant start,
    @NotNull
        @Min(1)
        @Schema(description = "How many consecutive hours from the start.", example = "2")
    Integer hours) {}
