package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by sessions five minutes into a session, once per participant.
 *
 * <p>It carries the code in clear because notifications has to email it and sessions only stores
 * its hash. The same warning as {@link AccessRequested} applies if events are ever persisted.
 */
public record PresenceCodeIssued(
    String tenantId,
    UUID sessionId,
    UUID userId,
    String code,
    Instant expiresAt,
    Instant occurredOn)
    implements DomainEvent {}
