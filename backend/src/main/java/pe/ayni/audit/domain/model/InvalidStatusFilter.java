package pe.ayni.audit.domain.model;

/** The status asked for is not one an anomaly can have. */
public class InvalidStatusFilter extends RuntimeException {

  public InvalidStatusFilter(String value) {
    super("The status " + value + " is not one of OPEN, DISMISSED or CONFIRMED");
  }
}
