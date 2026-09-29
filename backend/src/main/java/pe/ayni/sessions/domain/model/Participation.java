package pe.ayni.sessions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One participant's presence in a session: that they came, as whom, and when they first did.
 *
 * <p>Written the first time they join and never duplicated, as {@code
 * uq_participations_session_user} insists: a participant whose connection drops and comes back is
 * still one participant, and {@code joined_at} keeps the first arrival, which is what punctuality
 * is judged by. {@code end_confirmed_at} records whether, and when, they confirmed the end.
 */
@Entity
@Table(schema = "sessions", name = "participations")
public class Participation {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", length = 8, nullable = false, updatable = false)
  private ParticipantRole role;

  @Column(name = "joined_at")
  private Instant joinedAt;

  @Column(name = "left_at")
  private Instant leftAt;

  @Column(name = "connected_seconds", nullable = false)
  private int connectedSeconds;

  @Column(name = "end_confirmed_at")
  private Instant endConfirmedAt;

  protected Participation() {
    // Required by JPA
  }

  /** A participant arriving for the first time. */
  public static Participation arrived(
      UUID id, String tenantId, UUID sessionId, UUID userId, ParticipantRole role, Instant now) {
    Participation participation = new Participation();
    participation.id = Objects.requireNonNull(id, "id must not be null");
    participation.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    participation.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
    participation.userId = Objects.requireNonNull(userId, "userId must not be null");
    participation.role = Objects.requireNonNull(role, "role must not be null");
    participation.joinedAt = Objects.requireNonNull(now, "now must not be null");
    return participation;
  }

  /**
   * The participant says the session is over (US11).
   *
   * <p>Saying it again keeps the first time: it is the moment they declared the end, and a retried
   * request must not move it.
   *
   * @return whether this call is the one that confirmed it
   */
  public boolean confirmEnd(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    if (endConfirmedAt != null) {
      return false;
    }
    endConfirmedAt = now;
    return true;
  }

  public boolean hasConfirmedEnd() {
    return endConfirmedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public UUID getUserId() {
    return userId;
  }

  public ParticipantRole getRole() {
    return role;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public Instant getLeftAt() {
    return leftAt;
  }

  public int getConnectedSeconds() {
    return connectedSeconds;
  }

  public Instant getEndConfirmedAt() {
    return endConfirmedAt;
  }
}
