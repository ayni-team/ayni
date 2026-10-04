package pe.ayni.audit;

import org.junit.jupiter.api.Test;
import pe.ayni.audit.application.GetAuditMetricsUseCase;
import pe.ayni.audit.application.dto.AuditMetricsResponse;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAuditMetricsUseCaseTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final GetAuditMetricsUseCase useCase = new GetAuditMetricsUseCase(repository);

    @Test
    void shouldCalculateUsageMetricsCorrectly() {
        AuditLog log1 = new AuditLog("UPC", "user-1", "USER_LOGIN", "USER", "1", "Login OK");
        AuditLog log2 = new AuditLog("UPC", "user-2", "ANOMALOUS_SHORT_SESSION", "SESSION", "2", "Short session");
        AuditLog log3 = new AuditLog("UPC", "user-3", "ANOMALOUS_UNVERIFIED_SESSION", "SESSION", "3", "Unverified");

        when(repository.findByTenantId("UPC")).thenReturn(List.of(log1, log2, log3));

        AuditMetricsResponse metrics = useCase.execute("UPC");

        assertThat(metrics.getTenantId()).isEqualTo("UPC");
        assertThat(metrics.getTotalAuditLogs()).isEqualTo(3);
        assertThat(metrics.getTotalAnomalousAlerts()).isEqualTo(2);
        assertThat(metrics.getEventsByAction()).containsEntry("USER_LOGIN", 1L);
        assertThat(metrics.getEventsByAction()).containsEntry("ANOMALOUS_SHORT_SESSION", 1L);
    }
}