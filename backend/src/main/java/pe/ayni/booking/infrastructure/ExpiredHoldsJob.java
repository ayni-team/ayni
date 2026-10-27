package pe.ayni.booking.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.booking.application.ReleaseExpiredHoldsUseCase;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Returns expired holds to circulation, one university at a time.
 *
 * <p>Nobody makes this request, so each university is bound explicitly before anything is read, the
 * same way wallet expires credits and the hour horizon moves forward at night. It runs every minute
 * rather than at night because a hold lasts five minutes.
 */
@Component
class ExpiredHoldsJob {

  private static final Logger log = LoggerFactory.getLogger(ExpiredHoldsJob.class);

  private final ReleaseExpiredHoldsUseCase releaseExpiredHolds;
  private final IdentityApi identity;

  ExpiredHoldsJob(ReleaseExpiredHoldsUseCase releaseExpiredHolds, IdentityApi identity) {
    this.releaseExpiredHolds = releaseExpiredHolds;
    this.identity = identity;
  }

  @Scheduled(
      fixedDelayString = "${ayni.booking.hold-release-delay:PT1M}",
      initialDelayString = "${ayni.booking.hold-release-delay:PT1M}")
  void releaseExpiredHolds() {
    for (String tenantId : identity.activeTenantCodes()) {
      try {
        TenantContext.runAs(
            tenantId,
            () -> {
              int released = releaseExpiredHolds.forCurrentUniversity();
              if (released > 0) {
                log.info("Released {} expired holds in {}", released, tenantId);
              }
            });
      } catch (OptimisticLockingFailureException raced) {
        // A student confirmed, or took over, one of these hours while the sweep was running. The
        // sweep lost and rolled back; whatever is still expired is picked up on the next run.
        log.info("Releasing the expired holds of {} lost a race; retrying next run", tenantId);
      } catch (RuntimeException failure) {
        // Kept to one university: the others still get their hours back.
        log.error("Could not release the expired holds of {}", tenantId, failure);
      }
    }
  }
}
