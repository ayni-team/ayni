package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by identity when a student enters for the first time and their academic profile has
 * been imported.
 *
 * <p>Wallet reacts by granting the university's credits, and skills by enabling the courses the
 * student's grades already justify.
 */
public record StudentActivated(
    String tenantId, UUID userId, String email, String studentCode, Instant occurredOn)
    implements DomainEvent {}
