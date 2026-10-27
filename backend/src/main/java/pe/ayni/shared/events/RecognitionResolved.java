package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by recognition when a coordinator approves or rejects a request, always with a reason. */
public record RecognitionResolved(
    String tenantId,
    UUID requestId,
    UUID studentId,
    boolean approved,
    String reason,
    Instant occurredOn)
    implements DomainEvent {}
