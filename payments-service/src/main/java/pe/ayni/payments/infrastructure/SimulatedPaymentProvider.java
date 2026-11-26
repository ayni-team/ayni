package pe.ayni.payments.infrastructure;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.application.PaymentProviderGateway;
import pe.ayni.payments.application.PaymentProviderUnavailable;
import pe.ayni.payments.application.SimulatedPaymentSettings;
import pe.ayni.payments.application.SimulatedPaymentSettings.Outcome;

/**
 * Local payment adapter whose outcome can be switched without changing the payments use case.
 *
 * <p>It reads the outcomes from {@link SimulatedPaymentSettings} on every call, so a change takes
 * effect on the next purchase.
 */
@Component
class SimulatedPaymentProvider implements PaymentProviderGateway {

  private final SimulatedPaymentSettings settings;

  SimulatedPaymentProvider(SimulatedPaymentSettings settings) {
    this.settings = settings;
  }

  @Override
  public PaymentProvider.PaymentResult charge(UUID purchaseId, BigDecimal amount, String currency) {
    return resultOf(settings.chargeOutcome(), purchaseId);
  }

  @Override
  public Optional<PaymentProvider.PaymentResult> status(UUID purchaseId) {
    return Optional.of(resultOf(settings.resolutionOutcome(), purchaseId));
  }

  private static PaymentProvider.PaymentResult resultOf(Outcome outcome, UUID purchaseId) {
    if (outcome == Outcome.UNAVAILABLE) {
      throw new PaymentProviderUnavailable("The simulated payment provider is unavailable");
    }
    return new PaymentProvider.PaymentResult(
        PaymentProvider.PaymentResult.Outcome.valueOf(outcome.name()), "simulated-" + purchaseId);
  }
}
