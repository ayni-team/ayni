package pe.ayni.payments.application;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * What the simulated payment provider answers, readable and changeable while the application runs.
 *
 * <p>The two outcomes start from {@code ayni.payments.simulated-outcome} (the first charge of a
 * purchase) and {@code ayni.payments.simulated-resolution-outcome} (the later check of a pending
 * one). Nothing changes them except the demo profile, so with the default properties the provider
 * behaves as it always did. An initial value that is not allowed stops the application from
 * starting.
 */
@Component
public class SimulatedPaymentSettings {

  /** The outcomes the simulated provider can be set to. */
  public enum Outcome {
    CONFIRMED,
    REJECTED,
    PENDING,
    UNAVAILABLE;

    /**
     * The outcome with that exact name.
     *
     * @param setting what the value was given as, to say so in the message
     * @throws InvalidSimulatedOutcome when there is none
     */
    public static Outcome parse(String name, String setting) {
      for (Outcome outcome : values()) {
        if (outcome.name().equals(name)) {
          return outcome;
        }
      }
      throw new InvalidSimulatedOutcome(
          setting
              + " must be one of "
              + String.join(", ", Arrays.stream(values()).map(Enum::name).toList())
              + " but was "
              + (name == null ? "missing" : "'" + name + "'"));
    }
  }

  private volatile Outcome chargeOutcome;
  private volatile Outcome resolutionOutcome;

  SimulatedPaymentSettings(
      @Value("${ayni.payments.simulated-outcome:CONFIRMED}") String chargeOutcome,
      @Value("${ayni.payments.simulated-resolution-outcome:CONFIRMED}") String resolutionOutcome) {
    this.chargeOutcome = Outcome.parse(chargeOutcome, "ayni.payments.simulated-outcome");
    this.resolutionOutcome =
        Outcome.parse(resolutionOutcome, "ayni.payments.simulated-resolution-outcome");
  }

  /** What the provider answers to the first charge of a purchase. */
  public Outcome chargeOutcome() {
    return chargeOutcome;
  }

  /** What the provider answers when a pending purchase is checked again. */
  public Outcome resolutionOutcome() {
    return resolutionOutcome;
  }

  /** Changes the outcomes that are not null, both at once; the others stay as they are. */
  public synchronized void switchTo(Outcome chargeOutcome, Outcome resolutionOutcome) {
    if (chargeOutcome != null) {
      this.chargeOutcome = chargeOutcome;
    }
    if (resolutionOutcome != null) {
      this.resolutionOutcome = resolutionOutcome;
    }
  }
}
