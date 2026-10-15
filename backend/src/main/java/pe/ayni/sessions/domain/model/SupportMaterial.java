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
 * What the tutor reads to arrive prepared (US07): a summary of what the student needs and a few
 * suggestions of what to prepare, made from the need the student wrote when booking.
 *
 * <p>One per session. It is prepared once and not edited afterwards; a session without one shows
 * only the need.
 */
@Entity
@Table(schema = "sessions", name = "support_materials")
public class SupportMaterial {

  /** The longest summary kept, so it reads at a glance. */
  public static final int MAX_SUMMARY_LENGTH = 1000;

  /** The most suggestions kept. */
  public static final int MAX_SUGGESTIONS = 5;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "summary", nullable = false, updatable = false)
  private String summary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "suggestions", updatable = false)
  private List<String> suggestions;

  @Column(name = "generated_at", nullable = false, updatable = false)
  private Instant generatedAt;

  protected SupportMaterial() {
    // Required by JPA
  }

  /**
   * The material of a session, as the service that prepares it answered.
   *
   * @throws SessionRuleViolation when there is no summary
   */
  public static SupportMaterial preparedFor(
      Session session, String summary, List<String> suggestions, Instant now) {
    Objects.requireNonNull(session, "session must not be null");
    if (summary == null || summary.isBlank()) {
      throw new SessionRuleViolation("Support material needs a summary");
    }
    SupportMaterial material = new SupportMaterial();
    material.id = UUID.randomUUID();
    material.tenantId = session.getTenantId();
    material.sessionId = session.getId();
    material.summary = shorten(summary.strip());
    material.suggestions =
        suggestions == null
            ? List.of()
            : suggestions.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(suggestion -> !suggestion.isEmpty())
                .limit(MAX_SUGGESTIONS)
                .toList();
    material.generatedAt = Objects.requireNonNull(now, "now must not be null");
    return material;
  }

  private static String shorten(String text) {
    return text.length() <= MAX_SUMMARY_LENGTH ? text : text.substring(0, MAX_SUMMARY_LENGTH);
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

  public String getSummary() {
    return summary;
  }

  public List<String> getSuggestions() {
    return suggestions == null ? List.of() : suggestions;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }
}
