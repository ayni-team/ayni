package pe.ayni.shared.events;

import java.time.Instant;

/** Published by identity when a university is suspended. */
public record UniversitySuspended(String tenantId, Instant occurredOn) implements DomainEvent {}
