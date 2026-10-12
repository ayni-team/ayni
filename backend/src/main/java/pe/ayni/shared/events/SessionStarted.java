package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by sessions once, when both participants have checked in before the deadline. */
public record SessionStarted(String tenantId, UUID sessionId, UUID bookingId, Instant occurredOn)
    implements DomainEvent {}
