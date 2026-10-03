package pe.ayni.reputation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A historical tutor absence, recorded only when the student checked in. */
@Entity
@Table(schema = "reputation", name = "tutor_no_shows")
public class TutorNoShow {

  @Id
  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "catalog_item_id", nullable = false, updatable = false)
  private UUID catalogItemId;

  @Column(name = "occurred_on", nullable = false, updatable = false)
  private Instant occurredOn;

  protected TutorNoShow() {
    // Required by JPA
  }

  public static TutorNoShow recorded(
      UUID sessionId, String tenantId, UUID tutorId, UUID catalogItemId, Instant occurredOn) {
    TutorNoShow incident = new TutorNoShow();
    incident.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
    incident.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    incident.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    incident.catalogItemId =
        Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    incident.occurredOn = Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    return incident;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public Instant getOccurredOn() {
    return occurredOn;
  }
}
