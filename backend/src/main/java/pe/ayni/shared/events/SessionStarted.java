package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by sessions when the first participant joins. */
public record SessionStarted(String tenantId, UUID sessionId, UUID bookingId, Instant occurredOn)
    implements DomainEvent {}
