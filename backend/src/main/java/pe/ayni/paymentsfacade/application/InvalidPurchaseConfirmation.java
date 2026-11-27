package pe.ayni.paymentsfacade.application;

/** A PurchaseConfirmed from the payments service that lacks what the wallet needs. */
public class InvalidPurchaseConfirmation extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidPurchaseConfirmation(String message) {
    super(message);
  }
}
