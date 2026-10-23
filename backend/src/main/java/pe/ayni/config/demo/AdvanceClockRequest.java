package pe.ayni.config.demo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.format.DateTimeParseException;

/** How much to move the demo clock: an ISO 8601 duration, positive ahead, negative back. */
@Schema(name = "AdvanceDemoClockRequest")
public record AdvanceClockRequest(
    @Schema(
            description = "An ISO 8601 duration in days, hours, minutes or seconds",
            example = "PT2H")
        String by) {

  /** The duration it names, or a 400 when it names none. */
  Duration duration() {
    if (by == null || by.isBlank()) {
      throw new InvalidClockValue("by is required: an ISO 8601 duration, such as PT2H or -P1D");
    }
    try {
      return Duration.parse(by.trim());
    } catch (DateTimeParseException | ArithmeticException notADuration) {
      throw new InvalidClockValue("by must be an ISO 8601 duration, such as PT2H or -P1D");
    }
  }
}
