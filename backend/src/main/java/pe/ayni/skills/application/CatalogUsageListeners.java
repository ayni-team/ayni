package pe.ayni.skills.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/**
 * What skills keeps from the sessions that were taught, so the catalogue can be reviewed by use
 * (US44): which session was taught on which item, once.
 *
 * <p>Runs after the transaction of sessions commits, outside the request that closed the session,
 * so it binds the university from the event.
 */
@Component
class CatalogUsageListeners {

  private final TaughtSessionRepository taughtSessions;

  CatalogUsageListeners(TaughtSessionRepository taughtSessions) {
    this.taughtSessions = taughtSessions;
  }

  @ApplicationModuleListener
  void on(SessionCompleted event) {
    TenantContext.runAs(
        event.tenantId(),
        () ->
            taughtSessions.recordIfNew(
                event.sessionId(),
                event.tenantId(),
                event.catalogItemId(),
                event.tutorId(),
                event.occurredOn()));
  }
}
