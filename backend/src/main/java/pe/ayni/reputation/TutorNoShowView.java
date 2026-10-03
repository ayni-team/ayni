package pe.ayni.reputation;

import java.time.Instant;
import java.util.UUID;

/** A tutor absence in the compliance history. */
public record TutorNoShowView(UUID sessionId, UUID catalogItemId, Instant occurredOn) {}
