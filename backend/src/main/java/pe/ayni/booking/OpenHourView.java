package pe.ayni.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * One of a tutor's hours that can still be booked, as the other modules see it.
 *
 * <p>Carries only what a reader outside booking can act on: which block, and when. Who holds it and
 * why it is free stay inside booking, which decides at confirmation whether it still is.
 */
public record OpenHourView(UUID blockId, UUID tutorId, Instant startsAt, Instant endsAt) {}
