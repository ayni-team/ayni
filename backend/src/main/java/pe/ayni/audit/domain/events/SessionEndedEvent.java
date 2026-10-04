package pe.ayni.audit.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a session ends.
 */
public class SessionEndedEvent {

    private final String tenantId;
    private final UUID sessionId;
    private final String tutorId;
    private final Instant startedAt;
    private final Instant endedAt;

    /**
     * Constructs a SessionEndedEvent instance.
     *
     * @param tenantId  tenant identifier
     * @param sessionId session unique identifier
     * @param tutorId   tutor identifier
     * @param startedAt session start timestamp
     * @param endedAt   session end timestamp
     */
    public SessionEndedEvent(String tenantId, UUID sessionId, String tutorId, Instant startedAt, Instant endedAt) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.tutorId = tutorId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    /** @return tenant identifier */
    public String getTenantId() { return tenantId; }

    /** @return session unique identifier */
    public UUID getSessionId() { return sessionId; }

    /** @return tutor identifier */
    public String getTutorId() { return tutorId; }

    /** @return start timestamp */
    public Instant getStartedAt() { return startedAt; }

    /** @return end timestamp */
    public Instant getEndedAt() { return endedAt; }
}