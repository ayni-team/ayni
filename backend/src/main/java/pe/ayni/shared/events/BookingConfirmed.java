package pe.ayni.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/**
 * Published by booking once the credits were charged and the hours reserved, in the same
 * transaction.
 *
 * <p>Sessions creates the session, matching removes the blocks from the search, notifications tells
 * both participants.
 */
public record BookingConfirmed(
    String tenantId,
    UUID bookingId,
    UUID studentId,
    UUID tutorId,
    UUID catalogItemId,
    Instant startsAt,
    Instant endsAt,
    List<UUID> blockIds,
    Credits creditsCharged,
    Instant occurredOn)
    implements DomainEvent {}
