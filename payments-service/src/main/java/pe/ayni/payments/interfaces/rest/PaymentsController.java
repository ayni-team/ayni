package pe.ayni.payments.interfaces.rest;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.payments.application.PurchaseCreditsUseCase;
import pe.ayni.payments.application.PurchaseHistoryQuery;
import pe.ayni.payments.application.PurchaseOutcome;
import pe.ayni.payments.domain.model.PurchaseStatus;
import pe.ayni.payments.tenancy.CurrentUser;

/**
 * The purchases of credits, at the same paths and with the same answers the monolith had.
 *
 * <p>Only the monolith calls it (Strangler Fig, ADR 0011): it authenticates the student, forwards
 * the call with {@code X-Tenant-Id}, {@code X-User-Id} and {@code Idempotency-Key}, and passes the
 * answer back unchanged. Its OpenAPI description, written once, stays in the monolith.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentsController {

  private static final int MAX_IDEMPOTENCY_KEY = 64;

  private final PurchaseCreditsUseCase purchaseCredits;
  private final PurchaseHistoryQuery purchaseHistory;

  PaymentsController(
      PurchaseCreditsUseCase purchaseCredits, PurchaseHistoryQuery purchaseHistory) {
    this.purchaseCredits = purchaseCredits;
    this.purchaseHistory = purchaseHistory;
  }

  @GetMapping("/purchases")
  List<PurchaseResponse> purchases() {
    return purchaseHistory.forStudent(CurrentUser.require()).stream()
        .map(PurchaseResponse::of)
        .toList();
  }

  @GetMapping("/purchases/{purchaseId}")
  PurchaseDetailResponse purchaseDetail(@PathVariable UUID purchaseId) {
    return PurchaseDetailResponse.of(
        purchaseHistory.detailForStudent(CurrentUser.require(), purchaseId));
  }

  /**
   * 201 for a new purchase with a final result, 202 for a new one still pending (the provider has
   * no result, or did not answer), 200 for a key that was already used, with the purchase it made.
   */
  @PostMapping("/purchases")
  ResponseEntity<PurchaseResponse> purchase(
      @RequestBody PurchaseRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    if (request.credits() < 1
        || idempotencyKey.isEmpty()
        || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY) {
      throw new InvalidPurchaseRequest();
    }
    PurchaseOutcome outcome =
        purchaseCredits.execute(CurrentUser.require(), request.credits(), idempotencyKey);
    HttpStatus status =
        outcome.status() == PurchaseStatus.PENDING
            ? (outcome.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
            : (outcome.created() ? HttpStatus.CREATED : HttpStatus.OK);
    return ResponseEntity.status(status).body(PurchaseResponse.of(outcome));
  }
}
