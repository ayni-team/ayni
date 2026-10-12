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
    Instant confirmedAt) {

  static PurchaseResponse of(PurchaseOutcome outcome) {
    return new PurchaseResponse(
        outcome.id(),
        outcome.credits(),
        outcome.amount(),
        outcome.currency(),
        outcome.status(),
        outcome.providerReference(),
        outcome.createdAt(),
        outcome.confirmedAt());
  }
}
