package pe.ayni.booking.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.booking.application.HeldHours;

/** The hours now held for the student, and until when. */
@Schema(name = "HeldHours")
public record HeldHoursResponse(
    @Schema(description = "Tutor whose hours are held.",
        example = "22222222-2222-4222-8222-222222222222")
    UUID tutorId,
    @Schema(description = "Start of the first hour, UTC.", example = "2026-09-29T23:00:00Z")
    Instant startsAt,
    @Schema(description = "End of the last hour, UTC.", example = "2026-09-30T01:00:00Z")
    Instant endsAt,
    @Schema(description = "How many consecutive hours are held.", example = "2")
    int hours,
    @Schema(
        description =
            "When the hold runs out, UTC. Confirm before then; afterwards anybody may take the "
                + "hours.",
        example = "2026-09-24T02:05:00Z")
    Instant heldUntil,
    @Schema(description = "The one-hour blocks held, in order.",
        example = "[\"c0000000-0000-4000-8000-000000000001\"]")
    List<UUID> blockIds) {

  static HeldHoursResponse of(HeldHours held) {
    return new HeldHoursResponse(
        held.tutorId(),
        held.startsAt(),
        held.endsAt(),
        held.hours(),
        held.heldUntil(),
        held.blockIds());
  }
}
