package pe.ayni.recognition.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * A session that backs a request, as it was when the request was submitted.
 *
 * <p>The skill, the period and the rating are copied: a coordinator reading the file days later sees
 * what the student presented, not what changed since. A session backs one request at a time, which
 * the database enforces with a unique index on {@code (tenant_id, session_id)} for the rows that
 * were not released.
 *
 * <p>Rejecting a request releases its sessions (US29): the row stays, so the rejected file is still
 * what the student presented, and it is marked with the moment it was released. From then on the
 * session is free to back another request, and a second row for it, of the new request, is valid.
 * That is why the key is the request and the session together, the primary key of the table.
 *
 * <p>Because the key is assigned and not generated, Spring Data would take saving a session that
 * already backs a request for an update and quietly succeed. The entity says it is new until it was
 * saved or read, so saving it again is an insert and the unique index refuses it.
 */
@Entity
@Table(schema = "recognition", name = "request_sessions")
@IdClass(RequestedSession.Key.class)
public class RequestedSession implements Persistable<RequestedSession.Key> {

  /** The primary key of the table: the request and one of its sessions. */
  public static class Key implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID requestId;
    private UUID sessionId;

    protected Key() {
      // Required by JPA
    }

    public Key(UUID requestId, UUID sessionId) {
      this.requestId = requestId;
      this.sessionId = sessionId;
    }

    public UUID requestId() {
      return requestId;
    }

    public UUID sessionId() {
      return sessionId;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(requestId, key.requestId)
          && Objects.equals(sessionId, key.sessionId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(requestId, sessionId);
    }
  }

  @Id
  @Column(name = "request_id", nullable = false, updatable = false)
  private UUID requestId;

  @Id
  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "hours", nullable = false, updatable = false)
  private short hours;

  @Column(name = "catalog_item_id", nullable = false, updatable = false)
  private UUID catalogItemId;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "ended_at", nullable = false, updatable = false)
  private Instant endedAt;

  /** {@code null} when the tutor had no rating for the session at the time. */
  @Column(name = "stars", updatable = false)
  private Short stars;

  /** When the request it backs was rejected and the session became free again; {@code null} while it backs it. */
  @Column(name = "released_at")
  private Instant releasedAt;

  @Transient private boolean isNew = true;

  protected RequestedSession() {
    // Required by JPA
  }

  /** A session ready to back a request: the request is not known until it is built. */
  public static RequestedSession of(
      UUID sessionId,
      String tenantId,
      int hours,
      UUID catalogItemId,
      Instant startedAt,
      Instant endedAt,
      Integer stars) {
    RequestedSession session = new RequestedSession();
    session.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
    session.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    if (hours <= 0) {
      throw new IllegalArgumentException("hours must be positive");
    }
    session.hours = (short) hours;
    session.catalogItemId = Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    session.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
    session.endedAt = Objects.requireNonNull(endedAt, "endedAt must not be null");
    if (endedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("a session cannot end before it starts");
    }
    if (stars != null && (stars < 1 || stars > 5)) {
      throw new IllegalArgumentException("stars must be between 1 and 5");
    }
    session.stars = stars == null ? null : stars.shortValue();
    return session;
  }

  @Override
  public Key getId() {
    return new Key(requestId, sessionId);
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  void attachTo(UUID requestId) {
    this.requestId = requestId;
  }

  /**
   * The request it backs was rejected: the session is free to back another one. The row keeps
   * everything it was presented with. Releasing it again changes nothing.
   */
  public void release(Instant at) {
    Objects.requireNonNull(at, "at must not be null");
    if (releasedAt == null) {
      releasedAt = at;
    }
  }

  /** Whether the session no longer backs this request, because the request was rejected. */
  public boolean isReleased() {
    return releasedAt != null;
  }

  public Instant getReleasedAt() {
    return releasedAt;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public int getHours() {
    return hours;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public Integer getStars() {
    return stars == null ? null : stars.intValue();
  }
}
