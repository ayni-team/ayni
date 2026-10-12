package pe.ayni.audit.domain.model;

/**
 * Where an anomaly stands. Only {@code OPEN} can change: a coordinator dismisses it, and what was
 * decided is not reopened.
 */
public enum AnomalyStatus {
  OPEN,
  DISMISSED,
  CONFIRMED
}
