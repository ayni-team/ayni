package pe.ayni.audit.infrastructure.listeners;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.ayni.audit.domain.events.SessionEndedEvent;
import pe.ayni.audit.domain.events.SessionUnverifiedEvent;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;

import java.time.Duration;

/**
 * Event listener that records security and usage anomaly alerts in the audit log (US47).
 */
@Component
public class AnomalousUsageEventListener {

    private static final long MIN_SESSION_DURATION_MINUTES = 10;

    private final AuditLogRepository auditLogRepository;

    /**
     * Constructs the listener with the audit repository.
     *
     * @param auditLogRepository repository to persist audit entries
     */
    public AnomalousUsageEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Listens for session completion events and checks for anomalous short duration.
     *
     * @param event session ended event
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionEnded(SessionEndedEvent event) {
        if (event.getStartedAt() == null || event.getEndedAt() == null) {
            return;
        }

        long durationMinutes = Duration.between(event.getStartedAt(), event.getEndedAt()).toMinutes();

        if (durationMinutes < MIN_SESSION_DURATION_MINUTES) {
            AuditLog alert = new AuditLog(
                    event.getTenantId(),
                    event.getTutorId(),
                    "ANOMALOUS_SHORT_SESSION",
                    "SESSION",
                    event.getSessionId().toString(),
                    "Session ended abnormally early. Duration: " + durationMinutes + " minutes."
            );
            auditLogRepository.save(alert);
        }
    }

    /**
     * Listens for unverified session presence events.
     *
     * @param event session unverified event
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionUnverified(SessionUnverifiedEvent event) {
        AuditLog alert = new AuditLog(
                event.getTenantId(),
                event.getStudentId(),
                "ANOMALOUS_UNVERIFIED_SESSION",
                "SESSION",
                event.getSessionId().toString(),
                "Attendance verification failed: " + event.getReason()
        );
        auditLogRepository.save(alert);
    }
}