package pe.ayni.payments.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import pe.ayni.payments.application.SimulatedPaymentSettings.Outcome;

/**
 * The switches of the simulated payment provider that a presentation uses: what it answers, and the
 * circuit in front of it. Only under the {@code demo} profile.
 *
 * <p>Nothing is stored, so there is no transaction: the state is in memory and a restart takes it
 * back to the configuration.
 */
@Service
@Profile("demo")
public class SwitchSimulatedPaymentsUseCase {

  private final SimulatedPaymentSettings settings;
  private final PaymentCircuit circuit;

  SwitchSimulatedPaymentsUseCase(SimulatedPaymentSettings settings, PaymentCircuit circuit) {
    this.settings = settings;
    this.circuit = circuit;
  }

  /** The two outcomes in force and the state of the circuit. */
  public View read() {
    return new View(settings.chargeOutcome(), settings.resolutionOutcome(), circuit.status());
  }

  /**
   * Changes the outcomes that are given; null leaves one as it is. Both are checked before either
   * changes, so a bad value changes nothing.
   *
   * @throws InvalidSimulatedOutcome when a value is not allowed, or when none is given
   */
  public View switchTo(String chargeOutcome, String resolutionOutcome) {
    if (chargeOutcome == null && resolutionOutcome == null) {
      throw new InvalidSimulatedOutcome("chargeOutcome or resolutionOutcome is required");
    }
    Outcome charge = chargeOutcome == null ? null : Outcome.parse(chargeOutcome, "chargeOutcome");
    Outcome resolution =
        resolutionOutcome == null ? null : Outcome.parse(resolutionOutcome, "resolutionOutcome");
    settings.switchTo(charge, resolution);
    return read();
  }

  /** Closes the circuit, so the next purchase asks the provider again. */
  public View resetCircuit() {
    circuit.reset();
    return read();
  }

  public record View(Outcome chargeOutcome, Outcome resolutionOutcome, PaymentCircuit.Status circuit) {}
}
