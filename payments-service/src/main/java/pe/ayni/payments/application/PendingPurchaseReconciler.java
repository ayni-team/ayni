package pe.ayni.payments.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;
import pe.ayni.payments.tenancy.TenantContext;

/**
 * Asks the provider again about every pending purchase, and closes the ones past their deadline.
 *
 * <p>In the monolith it walked the active universities that identity listed. This service has no
 * identity to ask, and needs none: the universities to visit are the ones with a pending purchase
 * in its own database. Each one is then bound before its purchases are read, so row level security
 * shows the job only that university's rows, as it shows a request only its own.
 */
@Service
public class PendingPurchaseReconciler {

  private static final Logger log = LoggerFactory.getLogger(PendingPurchaseReconciler.class);

  private final PurchaseRepository purchases;
  private final PaymentProvider provider;
  private final ApplyPaymentResultUseCase applyResult;
  private final Clock clock;

  PendingPurchaseReconciler(
      PurchaseRepository purchases,
      PaymentProvider provider,
      ApplyPaymentResultUseCase applyResult,
      Clock clock) {
    this.purchases = purchases;
    this.provider = provider;
    this.applyResult = applyResult;
    this.clock = clock;
  }

  public void reconcile() {
    for (String tenantId : purchases.tenantsWithPendingPurchases()) {
      for (Purchase purchase :
          TenantContext.callAs(
              tenantId,
              () ->
                  purchases.findByTenantIdAndStatusOrderByCreatedAtAsc(
                      tenantId, PurchaseStatus.PENDING))) {
        try {
          TenantContext.runAs(tenantId, () -> reconcile(tenantId, purchase));
        } catch (RuntimeException failure) {
          // Kept to one purchase: the rest are still reconciled on this run.
          log.error(
              "Could not reconcile purchase {} for tenant {}", purchase.getId(), tenantId, failure);
        }
      }
    }
  }

  private void reconcile(String tenantId, Purchase purchase) {
    if (!purchase.getExpiresAt().isAfter(clock.instant())) {
      applyResult.expire(purchase.getId());
      log.info("Expired unresolved purchase {} for tenant {}", purchase.getId(), tenantId);
      return;
    }

    try {
      provider
          .status(purchase.getId())
          .filter(result -> result.outcome() != PaymentProvider.PaymentResult.Outcome.PENDING)
          .ifPresent(result -> applyResult.execute(purchase.getId(), result));
    } catch (PaymentProviderUnavailable unavailable) {
      log.warn(
          "Payment provider unavailable while checking purchase {} for tenant {}",
          purchase.getId(),
          tenantId);
    }
  }
}
