package pe.ayni.booking.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Hours a student has just taken out of circulation while they describe what they need.
 *
 * @param tutorName {@code null} when identity no longer knows the tutor
 * @param heldUntil when the hold runs out: the earliest deadline among the hours, since holding an
 *     hour again does not extend it
 * @param serverNow what time it is for the application when the hold was taken: the instant {@code
 *     heldUntil} was counted from, so a clock that is not the real one (the demo profile) cannot
 *     put the two out of step
 */
public record HeldHours(
    UUID tutorId,
    String tutorName,
    Instant startsAt,
    Instant endsAt,
    int hours,
    Instant heldUntil,
    Instant serverNow,
    List<UUID> blockIds) {}
