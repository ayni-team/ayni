package pe.ayni.wallet.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.application.ExpireCredits;

/**
 * Runs the expiry once a night, university by university.
 *
 * <p>The job holds no rule: it decides when, and {@code ExpireCredits} decides what. It binds the
 * university before calling, exactly as {@code TenantFilter} does for a request, so that a use case
 * reads it from {@link TenantContext} without caring whether it was started by a student or by the
 * clock.
 *
 * <p>Each university is a call of its own, and therefore a transaction of its own: one that fails
 * does not take the others with it.
 */
@Component
class CreditExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(CreditExpiryJob.class);

  private final ExpireCredits expireCredits;

  CreditExpiryJob(ExpireCredits expireCredits) {
    this.expireCredits = expireCredits;
  }

  /** Every night at three, when nobody is booking anything. */
  @Scheduled(cron = "${ayni.wallet.expiry-cron:0 0 3 * * *}", zone = "UTC")
  void expireOverdueCredits() {
    for (String tenantId : expireCredits.universitiesWithCreditsToExpire()) {
      try {
        TenantContext.runAs(
            tenantId,
            () -> {
              Credits lost = expireCredits.forCurrentUniversity();
              if (!lost.isZero()) {
                log.info("Expired {} credits in {}", lost.amount(), tenantId);
              }
            });
      } catch (RuntimeException failure) {
        // Kept to one university: the rest of them still get expired tonight.
        log.error("Could not expire the credits of {}", tenantId, failure);
      }
    }
  }
}
