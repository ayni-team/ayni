package pe.ayni.shared.events;

import java.time.Instant;

/** Published by identity when the platform administrator opens a new university. */
public record UniversityRegistered(String tenantId, String name, Instant occurredOn)
    implements DomainEvent {}
