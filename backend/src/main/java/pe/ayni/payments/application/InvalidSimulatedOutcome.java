package pe.ayni.payments.application;

/** A value that is not one of the outcomes the simulated payment provider can be set to. */
public class InvalidSimulatedOutcome extends RuntimeException {

  public InvalidSimulatedOutcome(String message) {
    super(message);
  }
}
