package pe.ayni.audit.domain.events;

import java.util.UUID;

/**
 * Event published when session presence/attendance verification fails.
 */
public class SessionUnverifiedEvent {

    private final String tenantId;
    private final UUID sessionId;
    private final String studentId;
    private final String reason;

    /**
     * Constructs a SessionUnverifiedEvent instance.
     *
     * @param tenantId  tenant identifier
     * @param sessionId session unique identifier
     * @param studentId student identifier
     * @param reason    reason for verification failure
     */
    public SessionUnverifiedEvent(String tenantId, UUID sessionId, String studentId, String reason) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.reason = reason;
    }

    /** @return tenant identifier */
    public String getTenantId() { return tenantId; }

    /** @return session unique identifier */
    public UUID getSessionId() { return sessionId; }

    /** @return student identifier */
    public String getStudentId() { return studentId; }

    /** @return reason details */
    public String getReason() { return reason; }
}