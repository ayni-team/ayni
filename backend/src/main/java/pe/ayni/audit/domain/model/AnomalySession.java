package pe.ayni.audit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** One session that supports an anomaly: what a coordinator can open to check it. */
@Entity
@Immutable
@IdClass(AnomalySessionId.class)
@Table(schema = "audit", name = "anomaly_sessions")
public class AnomalySession {

  @Id
  @Column(name = "anomaly_id", nullable = false, updatable = false)
  private UUID anomalyId;

  @Id
  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  protected AnomalySession() {
    // Required by JPA
  }

  public AnomalySession(UUID anomalyId, UUID sessionId, String tenantId) {
    this.anomalyId = Objects.requireNonNull(anomalyId, "anomalyId must not be null");
    this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
  }

  public UUID getAnomalyId() {
    return anomalyId;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public String getTenantId() {
    return tenantId;
  }
}
