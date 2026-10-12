package pe.ayni.wallet.infrastructure;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.application.ExpireCredits;
import pe.ayni.wallet.application.NotifyExpiringCredits;

/**
 * Processes due expirations and upcoming-expiry notices once a night, university by university.
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
  private final NotifyExpiringCredits notifyExpiringCredits;

  CreditExpiryJob(ExpireCredits expireCredits, NotifyExpiringCredits notifyExpiringCredits) {
    this.expireCredits = expireCredits;
    this.notifyExpiringCredits = notifyExpiringCredits;
  }

  /** Every night at three, when nobody is booking anything. */
  @Scheduled(cron = "${ayni.wallet.expiry-cron:0 0 3 * * *}", zone = "UTC")
  void processCreditExpiry() {
    Set<String> tenants = new LinkedHashSet<>(expireCredits.universitiesWithCreditsToExpire());
    tenants.addAll(notifyExpiringCredits.universitiesWithCreditsExpiring());
    for (String tenantId : tenants) {
      try {
        TenantContext.runAs(
            tenantId,
            () -> {
              Credits lost = expireCredits.forCurrentUniversity();
              int notifiedGroups = notifyExpiringCredits.forCurrentUniversity();
              if (!lost.isZero()) {
                log.info("Expired {} credits in {}", lost.amount(), tenantId);
              }
              if (notifiedGroups > 0) {
                log.info(
                    "Published expiry notices for {} credit groups in {}",
                    notifiedGroups,
                    tenantId);
              }
            });
      } catch (RuntimeException failure) {
        // Kept to one university: the rest of them still get expired tonight.
        log.error("Could not process credit expiry for {}", tenantId, failure);
      }
    }
  }
}
