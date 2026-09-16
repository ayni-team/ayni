package pe.ayni.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Published by skills when a tutor stops offering a catalogue item. Confirmed bookings stand. */
public record SkillWithdrawn(String tenantId, UUID tutorId, UUID catalogItemId, Instant occurredOn)
    implements DomainEvent {}
