package pe.ayni.wallet.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.application.LedgerIntegrityReport;
import pe.ayni.wallet.application.VerifyLedgerIntegrity;

/**
 * Verifies the ledger of each active university once a day, one university at a time.
 *
 * <p>A chain that fails is written to the log as an error, with the university and the place where
 * it breaks and nothing else: no amount, no student. Its delay is {@code
 * ayni.wallet.integrity-check-delay}, which the tests push out of reach and which they never wait
 * for: they call {@link VerifyLedgerIntegrity}, as this job does.
 */
@Component
class LedgerIntegrityJob {

  private static final Logger log = LoggerFactory.getLogger(LedgerIntegrityJob.class);

  private final VerifyLedgerIntegrity verify;
  private final IdentityApi identity;

  LedgerIntegrityJob(VerifyLedgerIntegrity verify, IdentityApi identity) {
    this.verify = verify;
    this.identity = identity;
  }

  @Scheduled(
      fixedDelayString = "${ayni.wallet.integrity-check-delay:PT24H}",
      initialDelayString = "${ayni.wallet.integrity-check-delay:PT24H}")
  void verifyLedgers() {
    for (String tenantId : identity.activeTenantCodes()) {
      try {
        LedgerIntegrityReport report =
            TenantContext.callAs(tenantId, verify::forCurrentUniversity);
        if (!report.intact()) {
          log.error(
              "The ledger of {} is not intact: the chain breaks at position {}",
              tenantId,
              report.firstInconsistentPosition());
        }
        if (report.balanceMismatches() > 0) {
          log.error(
              "The ledger of {} disagrees with the balance of {} accounts",
              tenantId,
              report.balanceMismatches());
        }
      } catch (RuntimeException failure) {
        // Kept to one university: the rest of them are still verified today.
        log.error("Could not verify the ledger of {}", tenantId, failure);
      }
    }
  }
}
