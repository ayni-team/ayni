package pe.ayni.audit.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;
import java.util.List;

/**
 * REST Controller for querying audit log records.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    /**
     * Constructs controller with required repository.
     *
     * @param auditLogRepository repository instance
     */
    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
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
}