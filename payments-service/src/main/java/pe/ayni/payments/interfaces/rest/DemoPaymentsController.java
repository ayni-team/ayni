package pe.ayni.payments.interfaces.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.payments.application.SwitchSimulatedPaymentsUseCase;

/**
 * The simulated payment provider of the demonstration, switched over HTTP.
 *
 * <p>Only under the {@code demo} profile: under any other the routes do not exist. The monolith
 * serves the same routes under {@code /api/v1/admin}, lets only a platform administrator in, and
 * forwards the call here; what each route does is documented there, in its OpenAPI description.
 */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/admin/demo/payments")
class DemoPaymentsController {

  private final SwitchSimulatedPaymentsUseCase payments;

  DemoPaymentsController(SwitchSimulatedPaymentsUseCase payments) {
    this.payments = payments;
  }

  @GetMapping
  DemoPaymentsResponse read() {
    return DemoPaymentsResponse.of(payments.read());
  }

  @PutMapping
  DemoPaymentsResponse switchTo(@RequestBody DemoPaymentsRequest request) {
    return DemoPaymentsResponse.of(
        payments.switchTo(request.chargeOutcome(), request.resolutionOutcome()));
  }

  @PostMapping("/circuit/reset")
  DemoPaymentsResponse resetCircuit() {
    return DemoPaymentsResponse.of(payments.resetCircuit());
  }
}
