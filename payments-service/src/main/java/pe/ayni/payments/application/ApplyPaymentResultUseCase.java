package pe.ayni.payments.application;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;
import pe.ayni.payments.tenancy.TenantContext;

@Service
class ApplyPaymentResultUseCase {

  private final PurchaseRepository purchases;
  private final PurchaseEvents events;
  private final Clock clock;

  ApplyPaymentResultUseCase(PurchaseRepository purchases, PurchaseEvents events, Clock clock) {
    this.purchases = purchases;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Applies what the provider answered. A confirmation records PurchaseConfirmed in the outbox in
   * this same transaction: the purchase is never confirmed without the fact the wallet needs, nor
   * the fact recorded for a purchase that was not confirmed.
   */
  @Transactional
  PurchaseOutcome execute(UUID purchaseId, PaymentProvider.PaymentResult result) {
    String tenantId = TenantContext.require();
    Purchase purchase =
        purchases
            .findByTenantIdAndId(tenantId, purchaseId)
            .orElseThrow(
                () -> new NoSuchElementException("Purchase not found: " + purchaseId));

    if (purchase.expire(clock.instant())) {
      purchases.save(purchase);
      return PurchaseOutcome.of(purchase, false);
    }
    if (purchase.getStatus() != PurchaseStatus.PENDING) {
      return PurchaseOutcome.of(purchase, false);
    }

    switch (result.outcome()) {
      case CONFIRMED -> {
        purchase.confirm(result.providerReference(), clock.instant());
        purchases.save(purchase);
        events.purchaseConfirmed(purchase);
      }
      case REJECTED -> {
        purchase.reject(result.providerReference());
        purchases.save(purchase);
      }
      case PENDING -> {
        purchase.awaitProviderResult(result.providerReference());
        purchases.save(purchase);
      }
    }
    return PurchaseOutcome.of(purchase, false);
  }

  @Transactional
  PurchaseOutcome expire(UUID purchaseId) {
    String tenantId = TenantContext.require();
    Purchase purchase =
        purchases
            .findByTenantIdAndId(tenantId, purchaseId)
            .orElseThrow(
                () -> new NoSuchElementException("Purchase not found: " + purchaseId));
    if (purchase.expire(clock.instant())) {
      purchases.save(purchase);
    }
    return PurchaseOutcome.of(purchase, false);
  }
}
