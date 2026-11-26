package pe.ayni.payments.infrastructure.outbox;

import java.util.UUID;

/** A fact recorded in {@code payments.outbox} that the monolith has not accepted yet. */
record OutboxMessage(UUID id, String tenantId, String eventType, String payload, int attempts) {}
