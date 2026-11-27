package pe.ayni.paymentsfacade.infrastructure;

/** The payments service did not answer, answered too late, or is refusing the monolith. */
public class PaymentsServiceUnavailable extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PaymentsServiceUnavailable(String message) {
    super(message);
  }

  public PaymentsServiceUnavailable(String message, Throwable cause) {
    super(message, cause);
  }
}
