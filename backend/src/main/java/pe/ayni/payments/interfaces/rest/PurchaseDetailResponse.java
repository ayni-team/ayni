package pe.ayni.payments.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.payments.application.PurchaseOutcome;
import pe.ayni.payments.domain.model.PurchaseStatus;

@Schema(description = "Purchase details and receipt information")
public record PurchaseDetailResponse(
    UUID id,
    int credits,
    BigDecimal amount,
    String currency,
    PurchaseStatus status,
    Instant purchasedAt,
    Instant confirmedAt,
    String providerReference,
    CreditMovementReference creditMovement) {

  static PurchaseDetailResponse of(PurchaseOutcome outcome) {
    return new PurchaseDetailResponse(
        outcome.id(),
        outcome.credits(),
        outcome.amount(),
        outcome.currency(),
        outcome.status(),
        outcome.createdAt(),
        outcome.confirmedAt(),
        outcome.providerReference(),
        outcome.status() == PurchaseStatus.CONFIRMED
            ? new CreditMovementReference(
                "PURCHASE",
                outcome.id(),
                "/api/v1/wallet/movements?reason=PURCHASE")
            : null);
  }

  @Schema(description = "Reference used to find the credit grant in wallet movement history")
  public record CreditMovementReference(
      @Schema(example = "PURCHASE") String type,
      UUID referenceId,
      @Schema(example = "/api/v1/wallet/movements?reason=PURCHASE") String historyUrl) {}
}
