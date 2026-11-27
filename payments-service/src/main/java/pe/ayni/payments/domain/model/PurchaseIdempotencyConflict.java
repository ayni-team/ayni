package pe.ayni.payments.domain.model;

public class PurchaseIdempotencyConflict extends RuntimeException {

  public PurchaseIdempotencyConflict() {
    super("This idempotency key was already used for a different purchase");
  }
}
