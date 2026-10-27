package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by recognition when a student submits a request to their university. */
public record RecognitionRequested(
    String tenantId, UUID requestId, UUID studentId, int totalHours, Instant occurredOn)
    implements DomainEvent {}
