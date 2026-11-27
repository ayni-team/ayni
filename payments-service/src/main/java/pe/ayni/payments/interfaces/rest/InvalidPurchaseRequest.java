package pe.ayni.payments.interfaces.rest;

/** The body or the headers of a purchase are not what the endpoint takes. */
class InvalidPurchaseRequest extends RuntimeException {

  private static final long serialVersionUID = 1L;

  InvalidPurchaseRequest() {
    super("The purchase request is invalid");
  }
}
