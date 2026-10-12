package pe.ayni.audit.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.shared.events.DomainEvent;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What audit detects as soon as a session ends: a presence that was not verified, and a completed
 * session that lasted far too little.
 *
 * <p>Detection runs after the publisher committed and never undoes it. A failure to detect is
 * logged without the data of the event and swallowed, as recording is: what happened has already
 * happened, and a coordinator is better served by a missing alert than by a session that could
 * not close.
 */
@Component
class AnomalyEventListeners {

  private static final Logger log = LoggerFactory.getLogger(AnomalyEventListeners.class);

  private final DetectSessionAnomaliesUseCase detect;

  AnomalyEventListeners(DetectSessionAnomaliesUseCase detect) {
    this.detect = detect;
  }

  @ApplicationModuleListener
  void on(SessionUnverified event) {
    inUniversityOf(event, "FAILED_PRESENCE", () -> detect.failedPresence(event));
  }

  @ApplicationModuleListener
  void on(SessionCompleted event) {
    inUniversityOf(event, "SHORT_DURATION", () -> detect.shortDuration(event));
  }

  private void inUniversityOf(DomainEvent event, String kind, Runnable detection) {
    try {
      TenantContext.runAs(event.tenantId(), detection);
    } catch (RuntimeException failure) {
      log.warn(
          "Could not look for {} in university {}: {}",
          kind,
          event.tenantId(),
          failure.getClass().getSimpleName());
    }
  }
}
