package pe.ayni.analytics.domain.model;

/** Somebody who is not an active coordinator of the university asked for its indicators. */
public class IndicatorsAccessDenied extends RuntimeException {

  public IndicatorsAccessDenied() {
    super("Only an active coordinator of the university can read its indicators");
  }
}
