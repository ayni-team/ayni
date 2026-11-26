package pe.ayni.payments.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.payments.domain.model.Purchase;
import pe.ayni.payments.domain.model.PurchaseStatus;

public record PurchaseOutcome(
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
    boolean created) {

  public static PurchaseOutcome of(Purchase purchase, boolean created) {
    return new PurchaseOutcome(
        purchase.getId(),
        purchase.getCredits(),
        purchase.getAmount(),
        purchase.getCurrency(),
        purchase.getStatus(),
        purchase.getProviderReference(),
        purchase.getCreatedAt(),
        purchase.getConfirmedAt(),
        purchase.getExpiresAt(),
        false,
        created);
  }

  public PurchaseOutcome asCreated() {
    return new PurchaseOutcome(
        id,
        credits,
        amount,
        currency,
        status,
        providerReference,
        createdAt,
        confirmedAt,
        expiresAt,
        providerUnavailable,
        true);
  }

  public PurchaseOutcome asProviderUnavailable() {
    return new PurchaseOutcome(
        id,
        credits,
        amount,
        currency,
        status,
        providerReference,
        createdAt,
        confirmedAt,
        expiresAt,
        true,
        created);
  }
}
