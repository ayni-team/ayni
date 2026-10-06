package pe.ayni.wallet;

public class CreditUseIdempotencyConflict extends RuntimeException {

  public CreditUseIdempotencyConflict() {
    super("This idempotency key was already used for a different credit use");
  }
}
