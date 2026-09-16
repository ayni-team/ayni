package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by booking when a tutor saves or changes their weekly availability. */
public record AvailabilityPublished(String tenantId, UUID tutorId, Instant occurredOn)
    implements DomainEvent {}
