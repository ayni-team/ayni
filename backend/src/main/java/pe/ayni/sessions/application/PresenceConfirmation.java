package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.UUID;

/** A participant's presence, confirmed. */
public record PresenceConfirmation(UUID sessionId, Instant confirmedAt) {}
