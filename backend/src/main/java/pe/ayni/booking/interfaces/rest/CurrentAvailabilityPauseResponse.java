package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;
import pe.ayni.booking.application.CurrentAvailabilityPause;

/** Whether the tutor is currently paused, and when that pause ends. */
@Schema(name = "CurrentAvailabilityPause")
public record CurrentAvailabilityPauseResponse(
    @Schema(example = "true") boolean paused,
    @Schema(description = "Identifier to reactivate this pause early.", nullable = true)
        UUID pauseId,
    @Schema(description = "First day included in the current pause.", nullable = true)
        LocalDate startsOn,
    @Schema(description = "Last day included in the current pause.", nullable = true)
        LocalDate endsOn) {

  static CurrentAvailabilityPauseResponse of(CurrentAvailabilityPause pause) {
    return new CurrentAvailabilityPauseResponse(
        pause.paused(), pause.pauseId(), pause.startsOn(), pause.endsOn());
  }
}
