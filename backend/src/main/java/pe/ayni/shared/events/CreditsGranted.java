package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;

/**
 * Published by wallet when credits arrive in an account.
 *
 * @param expiresAt {@code null} for credits that never expire
 */
public record CreditsGranted(
    String tenantId,
    UUID userId,
    Credits amount,
    CreditType type,
    Instant expiresAt,
    Instant occurredOn)
    implements DomainEvent {}
