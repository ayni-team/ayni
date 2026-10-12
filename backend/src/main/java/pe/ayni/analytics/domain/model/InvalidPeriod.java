package pe.ayni.analytics.domain.model;

/** The period asked for ends before it starts, or is longer than one request may cover. */
public class InvalidPeriod extends RuntimeException {

  public InvalidPeriod(String message) {
    super(message);
  }
}
