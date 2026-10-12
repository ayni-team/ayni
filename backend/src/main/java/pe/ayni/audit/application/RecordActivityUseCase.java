package pe.ayni.audit.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.audit.domain.model.ActivityEntry;
import pe.ayni.audit.infrastructure.ActivityLogRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Adds one entry to the activity log of the current university.
 *
 * <p>It runs in a transaction of its own. The listeners that call it must never be the reason an
 * operation fails, so whatever goes wrong here rolls back only this entry and reaches them as an
 * exception they can swallow, instead of marking their own transaction for rollback.
 */
@Service
class RecordActivityUseCase {

  private final ActivityLogRepository log;
  private final Clock clock;

  RecordActivityUseCase(ActivityLogRepository log, Clock clock) {
    this.log = log;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void record(NewActivity activity) {
    log.save(
        ActivityEntry.of(
            UUID.randomUUID(),
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
