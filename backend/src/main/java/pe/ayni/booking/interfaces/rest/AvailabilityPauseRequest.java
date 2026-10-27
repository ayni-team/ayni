package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request body for an inclusive availability pause. */
@Schema(name = "AvailabilityPauseRequest")
public record AvailabilityPauseRequest(
    @NotNull
        @Schema(
            description = "First calendar date included in the availability pause.",
            example = "2026-10-12")
    LocalDate startsOn,
    @NotNull
        @Schema(
            description = "Last calendar date included in the availability pause.",
            example = "2026-10-18")
    LocalDate endsOn) {}
