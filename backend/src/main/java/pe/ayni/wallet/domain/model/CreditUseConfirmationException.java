package pe.ayni.wallet.domain.model;

/**
 * A redemption or a donation that cannot use the confirmation it brings.
 *
 * <p>The {@link Reason} is what the answer names in its {@code code}, so the web application can
 * tell an expired warning, which only needs a new one, apart from the other refusals without
 * reading the message.
 */
public class CreditUseConfirmationException extends RuntimeException {

  /** Why the confirmation cannot be used. */
  public enum Reason {
    /** No confirmation was sent: the warning has to be requested first. */
    REQUIRED,
    /** The confirmation does not exist for this student. */
    INVALID,
    /** The confirmation outlived its validity. */
    EXPIRED,
    /** The confirmation was given for another operation, benefit or amount. */
    MISMATCH,
    /** The confirmation was already spent by another request. */
    USED
  }

  private final Reason reason;

  public CreditUseConfirmationException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
