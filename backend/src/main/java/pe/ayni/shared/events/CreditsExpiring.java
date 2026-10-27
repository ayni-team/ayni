package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/** Published by wallet ahead of the expiry of a group of credits, so the student can use them. */
public record CreditsExpiring(
    String tenantId, UUID userId, Credits amount, Instant expiresAt, Instant occurredOn)
    implements DomainEvent {}
