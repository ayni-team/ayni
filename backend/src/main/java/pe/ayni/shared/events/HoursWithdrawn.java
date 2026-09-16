package pe.ayni.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published by booking when available blocks stop being offered: the tutor removed availability,
 * paused, or the blocks were released without a booking.
 *
 * <p>Blocks taken by a booking are announced by {@link BookingConfirmed}, not here.
 */
public record HoursWithdrawn(
    String tenantId, UUID tutorId, List<UUID> blockIds, Instant occurredOn)
    implements DomainEvent {}
