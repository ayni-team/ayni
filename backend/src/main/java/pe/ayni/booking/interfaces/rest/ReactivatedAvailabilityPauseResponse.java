package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import pe.ayni.booking.application.ReactivatedAvailabilityPause;

/** A pause that ended early and how many hours became available again. */
@Schema(name = "ReactivatedAvailabilityPause")
public record ReactivatedAvailabilityPauseResponse(
    UUID pauseId,
    LocalDate startsOn,
    LocalDate endsOn,
    @Schema(description = "Previously withdrawn hours restored from the weekly pattern.")
        int restoredHours) {

  static ReactivatedAvailabilityPauseResponse of(ReactivatedAvailabilityPause reactivated) {
    return new ReactivatedAvailabilityPauseResponse(
        reactivated.pause().getId(),
        reactivated.pause().getStartsOn(),
        reactivated.pause().getEndsOn(),
        reactivated.hours().generation().blocksCreated());
  }
}
