package pe.ayni.wallet;

import pe.ayni.shared.domain.Credits;

/** Thrown when a charge exceeds the available balance. Carries how many credits are missing. */
public class InsufficientCreditsException extends RuntimeException {

  private final Credits missing;

  public InsufficientCreditsException(Credits missing) {
    super("Insufficient credits, missing " + missing.amount());
    this.missing = missing;
  }

  public Credits missing() {
    return missing;
  }
}
