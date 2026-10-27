package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by identity when the identity of a university changes: its logo, its colours or its
 * email domains. It says that it changed and who changed it, never the values.
 *
 * @param changedBy the coordinator or the platform administrator who made the change
 */
public record UniversityIdentityChanged(String tenantId, UUID changedBy, Instant occurredOn)
    implements DomainEvent {}
