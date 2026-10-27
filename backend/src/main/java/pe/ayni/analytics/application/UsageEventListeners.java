package pe.ayni.analytics.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import pe.ayni.analytics.domain.model.UnknownUniversity;
import pe.ayni.shared.events.DemandUncovered;
import pe.ayni.shared.events.DomainEvent;
import pe.ayni.shared.events.RecognitionResolved;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.StudentActivated;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What the usage indicators hear: a session completed, a student activated, a recognition decided
 * and a search that found no hour.
 *
 * <p>Projecting is never allowed to be the reason an operation fails, and these listeners run in the
 * tests of every module, where the other modules' interfaces are usually simulated. Whatever goes
 * wrong is caught here and said in the log in one line, without the stack and without the data of
 * the event. A university whose time zone cannot be read is not a fault of the projection: it is
 * only noted, at a level that is off by default.
 *
 * <p>The search that found no hour is the exception to how the others are heard. It comes from the
 * search, whose response time is measured, so it is not counted on the thread of the search: the
 * listener only leaves it in {@link UncoveredDemandQueue}. It is a plain listener, neither an {@code
 * @ApplicationModuleListener} nor a transactional one: either opens a transaction of its own, and
 * the event publication registry (TS04) stores and completes the delivery to a transactional listener
 * in a transaction of its own as well, so each would ask the pool for a second connection while the
 * search still holds the first. With fifty searches at once that exhausts the pool. A plain listener
 * is not stored by the registry, which is right for a count of searches that is not worth a retry.
 */
@Component
class UsageEventListeners {

  private static final Logger log = LoggerFactory.getLogger(UsageEventListeners.class);

  private final ProjectUsageUseCase projection;
  private final UncoveredDemandQueue uncoveredDemand;

  UsageEventListeners(ProjectUsageUseCase projection, UncoveredDemandQueue uncoveredDemand) {
    this.projection = projection;
    this.uncoveredDemand = uncoveredDemand;
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

  @EventListener
  void on(DemandUncovered event) {
    boolean accepted =
        uncoveredDemand.offer(
            () -> project(event, "DemandUncovered", () -> projection.demandUncovered(event)));
    if (!accepted) {
      log.warn("Not projecting DemandUncovered: too many are waiting to be counted");
    }
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
