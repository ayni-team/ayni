package pe.ayni.audit.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.ayni.audit.application.GetAuditMetricsUseCase;
import pe.ayni.audit.application.dto.AuditMetricsResponse;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;

import java.util.List;

/**
 * REST Controller for querying audit log records and usage metrics.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final GetAuditMetricsUseCase getAuditMetricsUseCase;

    /**
     * Constructs controller with required repository and use case.
     *
     * @param auditLogRepository    repository instance
     * @param getAuditMetricsUseCase use case instance
     */
    public AuditLogController(AuditLogRepository auditLogRepository, GetAuditMetricsUseCase getAuditMetricsUseCase) {
        this.auditLogRepository = auditLogRepository;
        this.getAuditMetricsUseCase = getAuditMetricsUseCase;
    }

    /**
     * Retrieves audit logs for a given tenant.
     *
     * @param tenantId tenant identifier header
     * @return response containing list of audit logs
     */
    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditLogs(@RequestHeader("X-Tenant-Id") String tenantId) {
        List<AuditLog> logs = auditLogRepository.findByTenantId(tenantId);
        return ResponseEntity.ok(logs);
    }

    /**
     * Retrieves usage indicators and analytics for a given tenant (US53).
     *
     * @param tenantId tenant identifier header
     * @return response containing analytics indicators
     */
    @GetMapping("/metrics")
    public ResponseEntity<AuditMetricsResponse> getMetrics(@RequestHeader("X-Tenant-Id") String tenantId) {
        AuditMetricsResponse metrics = getAuditMetricsUseCase.execute(tenantId);
        return ResponseEntity.ok(metrics);
    }
}