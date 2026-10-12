package pe.ayni.analytics.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pe.ayni.analytics.domain.model.UnknownUniversity;
import pe.ayni.shared.events.DomainEvent;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What the usage indicators hear: a session completed, a student activated and a recognition
 * decided.
 *
 * <p>Projecting is never allowed to be the reason an operation fails, and these listeners run in the
 * tests of every module, where the other modules' interfaces are usually simulated. Whatever goes
 * wrong is caught here and said in the log in one line, without the stack and without the data of
 * the event. A university whose time zone cannot be read is not a fault of the projection: it is
 * only noted, at a level that is off by default.
 */
@Component
class UsageEventListeners {

  private static final Logger log = LoggerFactory.getLogger(UsageEventListeners.class);

  private final ProjectUsageUseCase projection;

  UsageEventListeners(ProjectUsageUseCase projection) {
    this.projection = projection;
  }

  @ApplicationModuleListener
  void on(SessionCompleted event) {
    project(event, "SessionCompleted", () -> projection.sessionCompleted(event));
  }

  @ApplicationModuleListener
  void on(StudentActivated event) {
    project(event, "StudentActivated", () -> projection.studentActivated(event));
  }

  @ApplicationModuleListener
  void on(RecognitionResolved event) {
    project(event, "RecognitionResolved", () -> projection.recognitionResolved(event));
  }

  private void project(DomainEvent event, String what, Runnable projecting) {
    try {
      TenantContext.runAs(event.tenantId(), projecting);
    } catch (UnknownUniversity unknown) {
      log.debug("Not projecting {}: the university has no time zone to read", what);
    } catch (RuntimeException failure) {
      log.warn("Could not project {}: {}", what, failure.getClass().getSimpleName());
    }
  }
}
