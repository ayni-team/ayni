package pe.ayni.audit.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Key of {@link AnomalySession}: a session backs an anomaly once. */
public class AnomalySessionId implements Serializable {

  private UUID anomalyId;
  private UUID sessionId;

  public AnomalySessionId() {}

  public AnomalySessionId(UUID anomalyId, UUID sessionId) {
    this.anomalyId = anomalyId;
    this.sessionId = sessionId;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof AnomalySessionId other)) {
      return false;
    }
    return Objects.equals(anomalyId, other.anomalyId) && Objects.equals(sessionId, other.sessionId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(anomalyId, sessionId);
  }
}
