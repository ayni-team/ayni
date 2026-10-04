package pe.ayni.audit.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;
import java.util.UUID;

public interface SpringDataAuditLogRepository extends JpaRepository<AuditLog, UUID>, AuditLogRepository {
}