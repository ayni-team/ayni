package pe.ayni.booking.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.booking.application.HourBlockHorizon;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Keeps every tutor's hours a few weeks ahead, one university at a time.
 *
 * <p>Nobody makes this request, so each university is bound explicitly before anything is read,
 * the same way wallet expires credits at night.
 */
@Component
class HourBlockHorizonJob {

  private static final Logger log = LoggerFactory.getLogger(HourBlockHorizonJob.class);

  private final HourBlockHorizon horizon;
  private final IdentityApi identity;

  HourBlockHorizonJob(HourBlockHorizon horizon, IdentityApi identity) {
    this.horizon = horizon;
    this.identity = identity;
  }

  /** Every night at two, before the credits expire and long before anybody books. */
  @Scheduled(cron = "${ayni.booking.generation-cron:0 0 2 * * *}", zone = "UTC")
  void moveTheHorizonForward() {
    for (String tenantId : identity.activeTenantCodes()) {
      try {
        TenantContext.runAs(
            tenantId,
            () -> {
              int created = horizon.fillForCurrentUniversity();
              if (created > 0) {
                log.info("Generated {} hours in {}", created, tenantId);
              }
            });
      } catch (RuntimeException failure) {
        // Kept to one university: the others still get their hours tonight.
        log.error("Could not generate the hours of {}", tenantId, failure);
      }
    }
  }
}
