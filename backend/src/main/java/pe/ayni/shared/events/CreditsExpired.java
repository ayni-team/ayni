package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;
import pe.ayni.shared.domain.Credits;

/** Published by wallet when unspent credits reach their expiry and leave the balance. */
public record CreditsExpired(String tenantId, UUID userId, Credits amount, Instant occurredOn)
    implements DomainEvent {}
