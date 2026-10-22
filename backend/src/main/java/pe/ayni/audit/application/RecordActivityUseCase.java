package pe.ayni.audit.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Adds one entry to the activity log of the current university.
 *
 * <p>It runs in a transaction of its own. The listeners that call it must never be the reason an
 * operation fails, so whatever goes wrong here rolls back only this entry and reaches them as an
 * exception they can swallow, instead of marking their own transaction for rollback.
 *
 * <p>The entry is chained by {@link ActivityLogWriter}, inside this same transaction.
 */
@Service
class RecordActivityUseCase {

  private final ActivityLogWriter log;
  private final Clock clock;

  RecordActivityUseCase(ActivityLogWriter log, Clock clock) {
    this.log = log;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void record(NewActivity activity) {
    // An entry with an identifier of its own is written once: delivering its event again finds it
    // there. Two deliveries at the same moment end at the primary key, and the loser fails.
    if (activity.id() != null && log.contains(activity.id())) {
      return;
    }
    log.append(
        ActivityEntry.of(
            activity.id() != null ? activity.id() : UUID.randomUUID(),
            TenantContext.require(),
            activity.actorId(),
            activity.role(),
            activity.action(),
            activity.resourceType(),
            activity.resourceId(),
            activity.details(),
            activity.occurredAt() != null ? activity.occurredAt() : clock.instant()));
  }
}
