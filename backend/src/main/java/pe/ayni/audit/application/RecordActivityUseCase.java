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
 *
 * <p>An event delivered twice writes one entry (TS04): the entry's identifier comes from the event,
 * and an entry already there is not written again. Two deliveries at the same moment are settled
 * by the primary key, and the one that loses is swallowed by its listener like any other failure.
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
    String tenantId = TenantContext.require();
    UUID id = activity.entryId(tenantId);
    if (log.contains(id)) {
      // The same event delivered again: the entry is already there, and the log is append only.
      return;
    }
    log.append(
        ActivityEntry.of(
            id,
            tenantId,
            activity.actorId(),
            activity.role(),
            activity.action(),
            activity.resourceType(),
            activity.resourceId(),
            activity.details(),
            activity.occurredAt() != null ? activity.occurredAt() : clock.instant()));
  }
}
