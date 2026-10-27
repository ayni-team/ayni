package pe.ayni.payments.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * <p>Only under the {@code demo} profile: under any other the routes do not exist. They are under
 * {@code /api/v1/admin}, so the access filter lets only a platform administrator in.
 */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/admin/demo/payments")
class DemoPaymentsController {

  private static final String VIEW_EXAMPLE =
      """
      {"chargeOutcome":"UNAVAILABLE","resolutionOutcome":"CONFIRMED",\
      "circuit":{"state":"OPEN","consecutiveFailures":3,"openUntil":"2026-10-22T19:30:30Z"}}
      """;

  private final SwitchSimulatedPaymentsUseCase payments;

  DemoPaymentsController(SwitchSimulatedPaymentsUseCase payments) {
    this.payments = payments;
  }

  @Operation(
      summary = "Read the simulated payment provider",
      description =
          "The outcomes it answers now and the state of the circuit in front of it: closed, open"
              + " or half open, the failures in a row and until when it stays open. Demo profile"
              + " only.")
  @ApiResponse(
      responseCode = "200",
      description = "The outcomes and the circuit",
      content =
          @Content(
              schema = @Schema(implementation = DemoPaymentsResponse.class),
              examples = @ExampleObject(value = VIEW_EXAMPLE)))
  @GetMapping
  DemoPaymentsResponse read() {
    return DemoPaymentsResponse.of(payments.read());
  }

  @Operation(
      summary = "Switch what the simulated payment provider answers",
      description =
          "Changes the outcome of the first charge, of the later check of a pending purchase, or"
              + " both, from the next call on. The outcomes are CONFIRMED, REJECTED, PENDING and"
              + " UNAVAILABLE. Demo profile only.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      content =
          @Content(
              schema = @Schema(implementation = DemoPaymentsRequest.class),
              examples = {
                @ExampleObject(
                    name = "Reject purchases",
                    value = "{\"chargeOutcome\": \"REJECTED\"}"),
                @ExampleObject(
                    name = "Let pending purchases be confirmed",
                    value = "{\"resolutionOutcome\": \"CONFIRMED\"}"),
                @ExampleObject(
                    name = "Provider down",
                    value =
                        "{\"chargeOutcome\": \"UNAVAILABLE\", \"resolutionOutcome\":"
                            + " \"UNAVAILABLE\"}")
              }))
  @ApiResponse(
      responseCode = "200",
      description = "The outcomes and the circuit after the switch",
      content =
          @Content(
              schema = @Schema(implementation = DemoPaymentsResponse.class),
              examples = @ExampleObject(value = VIEW_EXAMPLE)))
  @ApiResponse(
      responseCode = "400",
      description = "No outcome is given, or one is not CONFIRMED, REJECTED, PENDING or UNAVAILABLE",
      content = @Content(schema = @Schema(implementation = PaymentsApiError.class)))
  @PutMapping
  DemoPaymentsResponse switchTo(@RequestBody DemoPaymentsRequest request) {
    return DemoPaymentsResponse.of(
        payments.switchTo(request.chargeOutcome(), request.resolutionOutcome()));
  }

  @Operation(
      summary = "Close the circuit of the payment provider",
      description =
          "Forgets the failures and lets the next purchase ask the provider again, without waiting"
              + " for the open period. Demo profile only.")
  @ApiResponse(
      responseCode = "200",
      description = "The outcomes and the closed circuit",
      content =
          @Content(
              schema = @Schema(implementation = DemoPaymentsResponse.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {"chargeOutcome":"CONFIRMED","resolutionOutcome":"CONFIRMED",\
                          "circuit":{"state":"CLOSED","consecutiveFailures":0,"openUntil":null}}
                          """)))
  @PostMapping("/circuit/reset")
  DemoPaymentsResponse resetCircuit() {
    return DemoPaymentsResponse.of(payments.resetCircuit());
  }
}
