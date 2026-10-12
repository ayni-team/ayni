package pe.ayni.payments.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pe.ayni.payments.domain.model.PurchaseStatus;

@Service
public class PurchaseCreditsUseCase {

  private static final Logger log = LoggerFactory.getLogger(PurchaseCreditsUseCase.class);

  private final StartPurchaseUseCase startPurchase;
  private final ApplyPaymentResultUseCase applyResult;
  private final PaymentProvider provider;

  PurchaseCreditsUseCase(
      StartPurchaseUseCase startPurchase,
      ApplyPaymentResultUseCase applyResult,
      PaymentProvider provider) {
    this.startPurchase = startPurchase;
    this.applyResult = applyResult;
    this.provider = provider;
  }

  public PurchaseOutcome execute(UUID studentId, int credits, String idempotencyKey) {
    PurchaseOutcome purchase = startPurchase.execute(studentId, credits, idempotencyKey);
    if (!purchase.created() || purchase.status() != PurchaseStatus.PENDING) {
      return purchase;
    }

    try {
      PaymentProvider.PaymentResult result =
          provider.charge(purchase.id(), purchase.amount(), purchase.currency());
      return applyResult.execute(purchase.id(), result).asCreated();
    } catch (PaymentProviderUnavailable unavailable) {
      log.warn("Payment provider unavailable while starting purchase {}", purchase.id());
      return purchase.asProviderUnavailable();
    }
  }
}
