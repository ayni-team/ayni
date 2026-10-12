package pe.ayni.audit.domain.model;

/** The anomaly was already decided, so it cannot be decided again. */
public class AnomalyAlreadyResolved extends RuntimeException {

  public AnomalyAlreadyResolved() {
    super("The anomaly was already resolved");
  }
}
