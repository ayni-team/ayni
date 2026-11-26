package pe.ayni.paymentsfacade.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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

  @Schema(description = "Reference used to find the credit grant in wallet movement history")
  public record CreditMovementReference(
      @Schema(example = "PURCHASE") String type,
      UUID referenceId,
      @Schema(example = "/api/v1/wallet/movements?reason=PURCHASE") String historyUrl) {}
}
