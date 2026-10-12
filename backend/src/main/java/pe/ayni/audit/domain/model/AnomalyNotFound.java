package pe.ayni.audit.domain.model;

/** There is no anomaly with that identifier in the university of the request. */
public class AnomalyNotFound extends RuntimeException {

  public AnomalyNotFound() {
    super("The anomaly does not exist in this university");
  }
}
