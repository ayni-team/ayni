package pe.ayni.audit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.audit.domain.events.SessionEndedEvent;
import pe.ayni.audit.domain.events.SessionUnverifiedEvent;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;
import pe.ayni.audit.infrastructure.listeners.AnomalousUsageEventListener;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnomalousUsageEventListenerTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AnomalousUsageEventListener listener = new AnomalousUsageEventListener(repository);

    @Test
    void shouldCreateAlertWhenSessionIsTooShort() {
        Instant now = Instant.now();
        Instant shortEnd = now.plus(5, ChronoUnit.MINUTES);

        SessionEndedEvent event = new SessionEndedEvent(
                "UPC", UUID.randomUUID(), "tutor-123", now, shortEnd
        );

        listener.handleSessionEnded(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("ANOMALOUS_SHORT_SESSION");
        assertThat(saved.getTenantId()).isEqualTo("UPC");
    }

    @Test
    void shouldCreateAlertWhenSessionUnverified() {
        SessionUnverifiedEvent event = new SessionUnverifiedEvent(
                "UPC", UUID.randomUUID(), "student-456", "Student did not join"
        );

        listener.handleSessionUnverified(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("ANOMALOUS_UNVERIFIED_SESSION");
    }
}