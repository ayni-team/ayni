package pe.ayni.payments.interfaces.rest;

import java.time.Instant;
import pe.ayni.payments.application.SwitchSimulatedPaymentsUseCase;

/** The outcomes the simulated payment provider answers and the state of the circuit before it. */
record DemoPaymentsResponse(String chargeOutcome, String resolutionOutcome, Circuit circuit) {

  /** {@code openUntil} is null while the circuit is closed. */
  record Circuit(String state, int consecutiveFailures, Instant openUntil) {}

  static DemoPaymentsResponse of(SwitchSimulatedPaymentsUseCase.View view) {
    return new DemoPaymentsResponse(
        view.chargeOutcome().name(),
        view.resolutionOutcome().name(),
        new Circuit(
            view.circuit().state().name(),
            view.circuit().consecutiveFailures(),
            view.circuit().openUntil()));
  }
}
