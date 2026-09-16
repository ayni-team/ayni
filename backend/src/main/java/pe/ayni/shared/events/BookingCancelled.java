package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by the booking module when a reservation is cancelled.
 *
 * <p>It carries whether the cancellation qualifies for a refund, so that wallet can post the
 * compensating entry without having to know the cancellation policy, which belongs to booking.
 */
public record BookingCancelled(
    String tenantId,
    UUID bookingId,
    UUID studentId,
    UUID tutorId,
    boolean refundable,
    Instant occurredOn)
    implements DomainEvent {}
