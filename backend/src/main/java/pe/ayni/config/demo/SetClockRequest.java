package pe.ayni.config.demo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** The instant to take the demo clock to, from which it keeps running. */
@Schema(name = "SetDemoClockRequest")
public record SetClockRequest(
    @Schema(description = "An ISO 8601 instant in UTC", example = "2026-10-22T19:50:00Z")
        String at) {

  /** The instant it names, or a 400 when it names none. */
  Instant instant() {
    if (at == null || at.isBlank()) {
      throw new InvalidClockValue(
          "at is required: an ISO 8601 instant, such as 2026-10-22T19:50:00Z");
    }
    try {
      return Instant.parse(at.trim());
    } catch (DateTimeParseException notAnInstant) {
      throw new InvalidClockValue(
          "at must be an ISO 8601 instant, such as 2026-10-22T19:50:00Z");
    }
  }
}
