package pe.ayni.payments.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.payments.application.PurchaseCreditsUseCase;
import pe.ayni.payments.application.PurchaseHistoryQuery;
import pe.ayni.payments.application.PurchaseOutcome;
import pe.ayni.shared.tenancy.CurrentUser;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
@Tag(name = "Payments", description = "Credit purchases using the simulated payment provider")
class PaymentsController {

  private final PurchaseCreditsUseCase purchaseCredits;
  private final PurchaseHistoryQuery purchaseHistory;

  PaymentsController(
      PurchaseCreditsUseCase purchaseCredits, PurchaseHistoryQuery purchaseHistory) {
    this.purchaseCredits = purchaseCredits;
    this.purchaseHistory = purchaseHistory;
  }

  @GetMapping("/purchases")
  @Operation(
      summary = "List the current student's credit purchases",
      description =
          "Shows pending purchases as well as their final outcome. For a pending purchase, follow "
              + "the guidance and check this list again instead of starting another payment.")
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Student making the request",
      schema =
          @Schema(
              type = "string",
              format = "uuid",
              example = "11111111-1111-4111-8111-111111111111"))
  List<PurchaseResponse> purchases() {
    return purchaseHistory.forStudent(CurrentUser.require()).stream()
        .map(PurchaseResponse::of)
        .toList();
  }

  @PostMapping("/purchases")
  @Operation(
      summary = "Buy credits for tutoring",
      description =
          """
          Starts a simulated payment for the requested credits. The monthly allowance is five
          confirmed credits per student, measured by UTC calendar month. The simulator charges
          S/ 5.00 per credit by default. Use a new Idempotency-Key for a new payment attempt.
          Confirmed credits are credited by the existing PurchaseConfirmed wallet listener and
          never expire.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Student making the purchase; never accepted in the request body",
      schema =
          @Schema(
              type = "string",
              format = "uuid",
              example = "11111111-1111-4111-8111-111111111111"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "Idempotency-Key",
      required = true,
      description = "Unique key for this student's purchase attempt",
      schema = @Schema(type = "string", example = "a6ad6642-01e3-4c0b-8f79-bff8c32f275f"))
  @ApiResponse(
      responseCode = "201",
      description = "The purchase attempt was created; status is CONFIRMED or FAILED",
      content =
          @Content(
              schema = @Schema(implementation = PurchaseResponse.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {
                            "id": "22222222-2222-4222-8222-222222222222",
                            "credits": 2,
                            "amount": 10.00,
                            "currency": "PEN",
                            "status": "CONFIRMED",
                            "providerReference": "simulated-22222222-2222-4222-8222-222222222222",
                            "createdAt": "2026-10-06T11:00:00Z",
                            "confirmedAt": "2026-10-06T11:00:00Z"
                          }
                          """)))
  @ApiResponse(
      responseCode = "200",
      description = "The purchase is already pending or the same idempotent request was repeated",
      content = @Content(schema = @Schema(implementation = PurchaseResponse.class)))
  @ApiResponse(
      responseCode = "202",
      description =
          "The provider has no final result yet. Check GET /api/v1/payments/purchases; do not retry",
      content = @Content(schema = @Schema(implementation = PurchaseResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "The monthly allowance was reached or the requested credits exceed what remains",
      content =
          @Content(
              schema = @Schema(implementation = PaymentsApiError.class),
              examples =
                  @ExampleObject(
                      value =
                          """
                          {
                            "timestamp": "2026-10-06T11:00:00Z",
                            "status": 409,
                            "error": "Conflict",
                            "message": "Monthly purchase limit is 5 credits; 4 already used; maximum available now is 1",
                            "path": "/api/v1/payments/purchases"
                          }
                          """)))
  ResponseEntity<PurchaseResponse> purchase(
      @Valid @RequestBody PurchaseRequest request,
      @RequestHeader("Idempotency-Key") @Size(min = 1, max = 64) String idempotencyKey) {
    PurchaseOutcome outcome =
        purchaseCredits.execute(CurrentUser.require(), request.credits(), idempotencyKey);
    HttpStatus status =
        outcome.status() == pe.ayni.payments.domain.model.PurchaseStatus.PENDING
            ? (outcome.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
            : (outcome.created() ? HttpStatus.CREATED : HttpStatus.OK);
    return ResponseEntity.status(status).body(PurchaseResponse.of(outcome));
  }
}
