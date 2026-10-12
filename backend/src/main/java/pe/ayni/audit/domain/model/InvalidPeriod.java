package pe.ayni.audit.domain.model;

/** The period asked for ends before it starts. */
public class InvalidPeriod extends RuntimeException {

  public InvalidPeriod() {
    super("The period ends before it starts");
  }
}
