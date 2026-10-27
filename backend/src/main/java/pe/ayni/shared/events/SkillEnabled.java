package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by skills when a tutor becomes enabled to teach a catalogue item, by either path. */
public record SkillEnabled(String tenantId, UUID tutorId, UUID catalogItemId, Instant occurredOn)
    implements DomainEvent {}
