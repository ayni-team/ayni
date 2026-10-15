package pe.ayni.sessions.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The shared whiteboard of a session (US10, ADR 0009).
 *
 * <p>Both participants draw on it while the session is in progress, and it stays as it was left
 * when the session closes, for the student to review it afterwards (US12). Every operation is
 * applied here, in the order the server takes them, and gives the board its next version; that
 * order is what makes both participants end up seeing the same board.
 *
 * <p>{@code hasActivity} becomes true with the first operation and never goes back, so the audit
 * can tell a session where nothing was drawn without reading the content.
 */
@Entity
@Table(schema = "sessions", name = "whiteboards")
public class Whiteboard {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content", nullable = false)
  private BoardContent content;

  @Column(name = "has_activity", nullable = false)
  private boolean hasActivity;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Whiteboard() {
    // Required by JPA
  }

  /** An empty board for a session, before anybody drew on it. */
  public static Whiteboard emptyFor(Session session, Instant now) {
    Objects.requireNonNull(session, "session must not be null");
    Whiteboard board = new Whiteboard();
    board.id = UUID.randomUUID();
    board.tenantId = session.getTenantId();
    board.sessionId = session.getId();
    board.content = BoardContent.EMPTY;
    board.hasActivity = false;
    board.updatedAt = Objects.requireNonNull(now, "now must not be null");
    return board;
  }

  /**
   * Applies an operation and gives the board its next version.
   *
   * @return the version this operation has
   * @throws WhiteboardRuleViolation when the board would grow past its limits
   */
  public long apply(BoardOperation operation, Instant now) {
    Objects.requireNonNull(operation, "operation must not be null");
    content = content().apply(operation);
    touch(now);
    return content.version();
  }

  /**
   * Replaces every element, when the one replacing it saw the current version.
   *
   * @return the new version
   * @throws WhiteboardVersionConflict when the board changed after {@code seenVersion}
   */
  public long replace(List<BoardElement> elements, long seenVersion, Instant now) {
    Objects.requireNonNull(elements, "elements must not be null");
    if (seenVersion != content().version()) {
      throw new WhiteboardVersionConflict(content().version());
    }
    content = content().replacedBy(elements);
    touch(now);
    return content.version();
  }

  private void touch(Instant now) {
    hasActivity = true;
    updatedAt = Objects.requireNonNull(now, "now must not be null");
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

  /** What it holds. A row created before any content, {@code '{}'}, reads as an empty board. */
  public BoardContent content() {
    return content == null || content.elements() == null ? BoardContent.EMPTY : content;
  }

  public boolean hasActivity() {
    return hasActivity;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
