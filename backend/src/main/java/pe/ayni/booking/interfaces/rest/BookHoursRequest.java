package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * What the contract asks for to book: tutor, skill, start, hours and need description. The hours
 * must be held by the student making the request.
 */
@Schema(name = "BookHoursRequest")
public record BookHoursRequest(
    @NotNull
        @Schema(description = "Tutor whose held hours are booked.",
            example = "22222222-2222-4222-8222-222222222222")
    UUID tutorId,
    @NotNull
        @Schema(description = "Catalogue item (skill) the session is about.",
            example = "b0000000-0000-4000-8000-000000000102")
    UUID catalogItemId,
    @NotNull
        @Schema(description = "Start of the first hour, in ISO 8601 UTC.",
            example = "2026-09-29T23:00:00Z")
    Instant start,
    @NotNull
        @Min(1)
        @Schema(description = "How many consecutive hours. Each one costs one credit.",
            example = "2")
    Integer hours,
    @NotBlank
        @Schema(description = "What the student needs help with. The tutor reads it beforehand.",
            example = "Normal forms and how to decompose a table before Friday's exam")
    String needDescription) {}
