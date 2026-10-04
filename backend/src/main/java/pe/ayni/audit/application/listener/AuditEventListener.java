package pe.ayni.audit.application.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import pe.ayni.audit.domain.model.AuditLog;
import pe.ayni.audit.domain.repository.AuditLogRepository;

@Component
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    public AuditEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    @EventListener
    public void handleGenericDomainEvent(Object event) {
        // Aquí capturaremos los eventos de dominio de booking, matching o wallet
        // Por ejemplo, mapeando el evento a una nueva instancia de AuditLog y llamando a auditLogRepository.save(...)
    }
}