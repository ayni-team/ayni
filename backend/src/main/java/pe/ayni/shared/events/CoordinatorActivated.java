package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by identity when an invited coordinator follows their link for the first time. */
public record CoordinatorActivated(String tenantId, UUID userId, Instant occurredOn)
    implements DomainEvent {}
