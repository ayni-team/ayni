package pe.ayni.skills.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * That a session was taught on a catalogue item, kept so the catalogue can be reviewed by use.
 *
 * <p>It is written only from the announcement of a verified session, by
 * {@code TaughtSessionRepository.recordIfNew}, so this class is read and never built.
 */
@Entity
@Table(schema = "skills", name = "taught_sessions")
public class TaughtSession {

  @Id
  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "catalog_item_id", nullable = false, updatable = false)
  private UUID catalogItemId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "completed_at", nullable = false, updatable = false)
  private Instant completedAt;

  protected TaughtSession() {
    // Required by JPA
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public UUID getTutorId() {
    return tutorId;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
