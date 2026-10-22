package pe.ayni.payments.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import pe.ayni.payments.application.SwitchSimulatedPaymentsUseCase;

/** The outcomes the simulated payment provider answers and the state of the circuit before it. */
@Schema(name = "SimulatedPaymentsView")
record DemoPaymentsResponse(
    @Schema(example = "REJECTED") String chargeOutcome,
    @Schema(example = "CONFIRMED") String resolutionOutcome,
    Circuit circuit) {

  @Schema(name = "SimulatedPaymentsCircuit")
  record Circuit(
      @Schema(allowableValues = {"CLOSED", "OPEN", "HALF_OPEN"}, example = "OPEN") String state,
      @Schema(example = "3") int consecutiveFailures,
      @Schema(
              description = "Until when the circuit refuses calls; null while it is closed",
              example = "2026-10-22T19:30:30Z")
          Instant openUntil) {}

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
