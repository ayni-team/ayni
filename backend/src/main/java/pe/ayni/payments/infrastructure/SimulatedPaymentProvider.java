package pe.ayni.payments.infrastructure;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.application.PaymentProviderUnavailable;

/** Local payment adapter whose outcome can be switched without changing the payments use case. */
@Component
class SimulatedPaymentProvider implements PaymentProvider {

  private final String outcome;
  private final String lookupOutcome;

  SimulatedPaymentProvider(
      @Value("${ayni.payments.simulated-outcome:CONFIRMED}") String outcome,
      @Value("${ayni.payments.simulated-resolution-outcome:CONFIRMED}") String lookupOutcome) {
    this.outcome = outcome;
    this.lookupOutcome = lookupOutcome;
  }

  @Override
  public PaymentResult charge(UUID purchaseId, BigDecimal amount, String currency) {
    if ("UNAVAILABLE".equals(outcome)) {
      throw new PaymentProviderUnavailable("The simulated payment provider is unavailable");
    }
    return new PaymentResult(PaymentResult.Outcome.valueOf(outcome), "simulated-" + purchaseId);
  }

  @Override
  public Optional<PaymentResult> status(UUID purchaseId) {
    if ("UNAVAILABLE".equals(lookupOutcome)) {
      throw new PaymentProviderUnavailable("The simulated payment provider is unavailable");
    }
    return Optional.of(
        new PaymentResult(
            PaymentResult.Outcome.valueOf(lookupOutcome), "simulated-" + purchaseId));
  }
}
