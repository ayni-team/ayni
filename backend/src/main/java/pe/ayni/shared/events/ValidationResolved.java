package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by skills when a coordinator approves or rejects the evidence for a global tool. */
public record ValidationResolved(
    String tenantId,
    UUID validationRequestId,
    UUID tutorId,
    UUID catalogItemId,
    boolean approved,
    String reason,
    Instant occurredOn)
    implements DomainEvent {}
