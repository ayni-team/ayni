package pe.ayni.audit.domain.model;

/** An anomaly is dismissed with a reason, and the one given is missing or too long. */
public class InvalidDismissalReason extends RuntimeException {

  public InvalidDismissalReason(String message) {
    super(message);
  }
}
