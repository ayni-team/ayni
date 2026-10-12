package pe.ayni.payments.domain.model;

/** A monthly allowance shared by a student's confirmed credit purchases. */
public record PurchaseLimit(int credits) {

  public PurchaseLimit {
    if (credits < 1) {
      throw new IllegalArgumentException("A purchase limit must be at least one credit");
    }
  }

  public int remainingAfter(long used) {
    if (used < 0) {
      throw new IllegalArgumentException("Used credits cannot be negative");
    }
    return used >= credits ? 0 : credits - (int) used;
  }

  public void check(int requested, long used) {
    if (requested < 1) {
      throw new PurchaseRuleViolation("A purchase must contain at least one credit");
    }
    if (used < 0) {
      throw new IllegalArgumentException("Used credits cannot be negative");
    }
    if (requested > remainingAfter(used)) {
      throw new PurchaseLimitExceeded(credits, used, remainingAfter(used));
    }
  }
}
