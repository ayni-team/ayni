package pe.ayni.payments.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.ayni.payments.application.PaymentProvider;

/** Local payment adapter whose outcome can be switched without changing the payments use case. */
@Component
class SimulatedPaymentProvider implements PaymentProvider {

  private final PaymentResult.Outcome outcome;

  SimulatedPaymentProvider(
      @Value("${ayni.payments.simulated-outcome:CONFIRMED}") PaymentResult.Outcome outcome) {
    this.outcome = outcome;
  }

  @Override
  public PaymentResult charge(UUID purchaseId, BigDecimal amount, String currency) {
    return new PaymentResult(outcome, "simulated-" + purchaseId);
  }
}
