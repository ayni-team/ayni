package pe.ayni.payments.application;

import java.util.UUID;

public class PurchaseNotFoundException extends RuntimeException {

  public PurchaseNotFoundException(UUID purchaseId) {
    super("Purchase not found: " + purchaseId);
  }
}
