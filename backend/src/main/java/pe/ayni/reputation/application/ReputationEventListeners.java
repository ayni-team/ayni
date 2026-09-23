package pe.ayni.reputation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Updates reputation projections from events published by other modules.
 */
@Component
class ReputationEventListeners {

    private static final Logger log =
            LoggerFactory.getLogger(ReputationEventListeners.class);

    private final RecordCompletedSession recordCompletedSession;

    ReputationEventListeners(RecordCompletedSession recordCompletedSession) {
        this.recordCompletedSession = recordCompletedSession;
    }

    @ApplicationModuleListener
    void on(SessionCompleted event) {
        TenantContext.runAs(
                event.tenantId(),
                () ->
                        recordCompletedSession.record(
                                event.tutorId(),
                                event.catalogItemId(),
                                event.occurredOn()));

        log.debug(
                "Updated tutor standing after completed session {}",
                event.sessionId());
    }
}