package pe.ayni.payments.domain.model;

public class PurchaseLimitExceeded extends RuntimeException {

  private final int limit;
  private final long used;
  private final int maximumNow;

  public PurchaseLimitExceeded(int limit, long used, int maximumNow) {
    super(
        "Monthly purchase limit is "
            + limit
            + " credits; "
            + used
            + " already used; maximum available now is "
            + maximumNow);
    this.limit = limit;
    this.used = used;
    this.maximumNow = maximumNow;
  }

  public int limit() {
    return limit;
  }

  public long used() {
    return used;
  }

  public int maximumNow() {
    return maximumNow;
  }
}
