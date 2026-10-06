package pe.ayni.payments.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.ayni.identity.IdentityApi;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;
import pe.ayni.payments.infrastructure.PurchaseRepository;
import pe.ayni.shared.tenancy.TenantContext;

@Service
public class PendingPurchaseReconciler {

  private static final Logger log = LoggerFactory.getLogger(PendingPurchaseReconciler.class);

  private final PurchaseRepository purchases;
  private final PaymentProvider provider;
  private final ApplyPaymentResultUseCase applyResult;
  private final IdentityApi identity;
  private final Clock clock;

  PendingPurchaseReconciler(
      PurchaseRepository purchases,
      PaymentProvider provider,
      ApplyPaymentResultUseCase applyResult,
      IdentityApi identity,
      Clock clock) {
    this.purchases = purchases;
    this.provider = provider;
    this.applyResult = applyResult;
    this.identity = identity;
    this.clock = clock;
  }

  public void reconcile() {
    for (String tenantId : identity.activeTenantCodes()) {
      for (Purchase purchase :
          purchases.findByTenantIdAndStatusOrderByCreatedAtAsc(
              tenantId, PurchaseStatus.PENDING)) {
        TenantContext.runAs(tenantId, () -> reconcile(tenantId, purchase));
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
