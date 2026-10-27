package pe.ayni.config.demo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The clock of the demonstration as the application reads it.
 *
 * @param now what every rule of the application reads as the current instant
 * @param realNow the real time at the same moment
 * @param offset how far {@code now} is from {@code realNow}, an ISO 8601 duration
 */
@Schema(name = "DemoClock", description = "The time the application reads and the real time")
public record DemoClockView(
    @Schema(example = "2026-10-23T17:05:00Z") Instant now,
    @Schema(example = "2026-10-21T15:05:00Z") Instant realNow,
    @Schema(example = "PT50H") String offset) {}
