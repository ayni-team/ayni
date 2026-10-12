package pe.ayni.payments.application;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.infrastructure.PurchaseRepository;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.PurchaseConfirmed;
import pe.ayni.shared.tenancy.TenantContext;

@Service
class ApplyPaymentResultUseCase {

  private final PurchaseRepository purchases;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  ApplyPaymentResultUseCase(
      PurchaseRepository purchases, ApplicationEventPublisher events, Clock clock) {
    this.purchases = purchases;
    this.events = events;
    this.clock = clock;
  }

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
    if (purchase.getStatus() != pe.ayni.payments.domain.model.PurchaseStatus.PENDING) {
      return PurchaseOutcome.of(purchase, false);
    }

    switch (result.outcome()) {
      case CONFIRMED -> {
        purchase.confirm(result.providerReference(), clock.instant());
        events.publishEvent(
            new PurchaseConfirmed(
                tenantId,
                purchase.getId(),
                purchase.getStudentId(),
                Credits.of(purchase.getCredits()),
                purchase.getConfirmedAt()));
      }
      case REJECTED -> purchase.reject(result.providerReference());
      case PENDING -> purchase.awaitProviderResult(result.providerReference());
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
