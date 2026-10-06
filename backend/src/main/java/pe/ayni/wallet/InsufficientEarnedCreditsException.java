package pe.ayni.wallet;

import pe.ayni.shared.domain.Credits;

public class InsufficientEarnedCreditsException extends RuntimeException {

  private final Credits missing;

  public InsufficientEarnedCreditsException(Credits missing) {
    super("Insufficient earned credits, missing " + missing.amount());
    this.missing = missing;
  }

  public Credits missing() {
    return missing;
  }
}
