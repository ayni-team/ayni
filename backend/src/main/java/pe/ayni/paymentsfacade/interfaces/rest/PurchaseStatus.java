package pe.ayni.paymentsfacade.interfaces.rest;

/** Where a purchase is, as the payments service answers it. */
public enum PurchaseStatus {
  PENDING,
  CONFIRMED,
  FAILED,
  EXPIRED
}
