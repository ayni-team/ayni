package pe.ayni.audit.domain.repository;

import pe.ayni.audit.domain.model.AuditLog;
import java.util.List;

public interface AuditLogRepository {
    AuditLog save(AuditLog auditLog);
    List<AuditLog> findByTenantId(String tenantId);
}