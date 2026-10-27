package pe.ayni.shared.events;

import java.time.Instant;

/**
 * Published by identity when somebody asks for a single use access link.
 *
 * <p>It carries the link itself because notifications has to deliver it and identity only stores
 * its hash. The event publication registry stores it without the link and without the email
 * (TS04, {@code config.RedactingEventSerializer}), so a delivery resumed from the registry has
 * neither, and notifications drops it.
 *
 * @param tenantId the university, or {@code null} for the platform administrator
 * @param purpose ACTIVATION, LOGIN or COORDINATOR_INVITE
 */
public record AccessRequested(
    String tenantId,
    String email,
    String purpose,
    String accessLink,
    Instant expiresAt,
    Instant occurredOn)
    implements DomainEvent {}
