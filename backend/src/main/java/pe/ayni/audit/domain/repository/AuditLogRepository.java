package pe.ayni.audit.domain.repository;

import pe.ayni.audit.domain.model.AuditLog;
import java.util.List;

/**
 * Repository interface for managing audit logs.
 */
public interface AuditLogRepository {

    /**
     * Saves a new audit log entry.
     *
     * @param auditLog entry to save
     * @return saved audit log
     */
    AuditLog save(AuditLog auditLog);

    /**
     * Finds audit logs by tenant identifier.
     *
     * @param tenantId tenant identifier
     * @return list of audit logs
     */
    List<AuditLog> findByTenantId(String tenantId);
}