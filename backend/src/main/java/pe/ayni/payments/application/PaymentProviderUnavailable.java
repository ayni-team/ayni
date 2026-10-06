package pe.ayni.payments.application;

/** The provider could not be reached; the outcome of the charge remains unknown. */
public class PaymentProviderUnavailable extends RuntimeException {

  public PaymentProviderUnavailable(String message, Throwable cause) {
    super(message, cause);
  }

  public PaymentProviderUnavailable(String message) {
    super(message);
  }
}
