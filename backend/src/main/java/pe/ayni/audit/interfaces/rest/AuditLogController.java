package pe.ayni.audit.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditLogs(@RequestHeader("X-Tenant-Id") String tenantId) {
        List<AuditLog> logs = auditLogRepository.findByTenantId(tenantId);
        return ResponseEntity.ok(logs);
    }
}