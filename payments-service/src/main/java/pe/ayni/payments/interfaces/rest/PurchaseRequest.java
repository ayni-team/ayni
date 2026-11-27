package pe.ayni.payments.interfaces.rest;

/** The body of a purchase. Checked by hand in the controller; the service has no Bean Validation. */
public record PurchaseRequest(int credits) {}
