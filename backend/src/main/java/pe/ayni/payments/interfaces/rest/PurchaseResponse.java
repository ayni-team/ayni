package pe.ayni.payments.interfaces.rest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.payments.application.PurchaseOutcome;
import pe.ayni.payments.domain.model.PurchaseStatus;

public record PurchaseResponse(
    UUID id,
    int credits,
    BigDecimal amount,
    String currency,
    PurchaseStatus status,
    String providerReference,
    Instant createdAt,
    Instant confirmedAt,
    Instant expiresAt,
    boolean providerUnavailable,
    String guidance) {

  static PurchaseResponse of(PurchaseOutcome outcome) {
    return new PurchaseResponse(
        outcome.id(),
        outcome.credits(),
        outcome.amount(),
        outcome.currency(),
        outcome.status(),
        outcome.providerReference(),
        outcome.createdAt(),
        outcome.confirmedAt(),
        outcome.expiresAt(),
        outcome.providerUnavailable(),
        outcome.providerUnavailable()
            ? "Credit purchases are temporarily unavailable. This attempt remains pending; do not retry. Check this purchase's status."
            : outcome.status() == PurchaseStatus.PENDING
                ? "Payment is still being processed. Do not start another purchase; check this purchase's status."
                : null);
  }
}
