package pe.ayni.payments.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Boundary to the payment provider; the current adapter simulates its response. */
public interface PaymentProvider {

  PaymentResult charge(UUID purchaseId, BigDecimal amount, String currency);

  Optional<PaymentResult> status(UUID purchaseId);

  record PaymentResult(Outcome outcome, String providerReference) {

    public PaymentResult {
      if (outcome == null) {
        throw new IllegalArgumentException("outcome must not be null");
      }
      if (providerReference == null || providerReference.isBlank()) {
        throw new IllegalArgumentException("providerReference must not be blank");
      }
    }

    public enum Outcome {
      CONFIRMED,
      REJECTED,
      PENDING
    }
  }
}
