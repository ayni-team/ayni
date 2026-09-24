package pe.ayni.booking.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Hours a student has just taken out of circulation while they describe what they need.
 *
 * @param heldUntil when the hold runs out: the earliest deadline among the hours, since holding an
 *     hour again does not extend it
 */
public record HeldHours(
    UUID tutorId,
    Instant startsAt,
    Instant endsAt,
    int hours,
    Instant heldUntil,
    List<UUID> blockIds) {}
