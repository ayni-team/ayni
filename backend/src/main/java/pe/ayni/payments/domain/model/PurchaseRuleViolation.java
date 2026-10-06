package pe.ayni.payments.domain.model;

public class PurchaseRuleViolation extends RuntimeException {

  public PurchaseRuleViolation(String message) {
    super(message);
  }
}
