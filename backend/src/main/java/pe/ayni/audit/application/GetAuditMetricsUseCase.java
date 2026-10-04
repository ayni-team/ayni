package pe.ayni.audit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.application.dto.AuditMetricsResponse;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only use case for retrieving usage indicators and audit analytics for a tenant (US53).
 */
@Service
public class GetAuditMetricsUseCase {

    private final AuditLogRepository auditLogRepository;

    /**
     * Constructs the use case with the repository.
     *
     * @param auditLogRepository repository to fetch logs
     */
    public GetAuditMetricsUseCase(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Computes usage indicators for a specific tenant.
     *
     * @param tenantId tenant identifier
     * @return calculated usage metrics
     */
    @Transactional(readOnly = true)
    public AuditMetricsResponse execute(String tenantId) {
        List<AuditLog> logs = auditLogRepository.findByTenantId(tenantId);

        long totalLogs = logs.size();

        long totalAnomalies = logs.stream()
                .filter(log -> log.getAction() != null && log.getAction().startsWith("ANOMALOUS_"))
                .count();

        Map<String, Long> eventsByAction = logs.stream()
                .filter(log -> log.getAction() != null)
                .collect(Collectors.groupingBy(AuditLog::getAction, Collectors.counting()));

        return new AuditMetricsResponse(tenantId, totalLogs, totalAnomalies, eventsByAction);
    }
}