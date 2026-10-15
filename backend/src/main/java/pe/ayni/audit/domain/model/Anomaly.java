package pe.ayni.audit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A pattern that suggests credits are being accumulated artificially, waiting for a coordinator.
 *
 * <p>It is raised open and leaves that state once: a coordinator dismisses it with a reason, and
 * from then on it is a closed decision that is not reopened. The one who decided and the moment
 * stay with it.
 *
 * <p>The subject is the tutor whose credits the pattern puts in question. The other people involved
 * and the figures that made it an anomaly are in the detail, a flat map of strings that holds
 * identifiers and numbers, never an address or a name.
 */
@Entity
@Table(schema = "audit", name = "anomalies")
public class Anomaly {

  /** Longest reason a dismissal may carry, which is what the column holds. */
  public static final int MAX_REASON_LENGTH = 500;

  /** Names in the detail that point at a person, and the role they had. */
  private static final Map<String, String> PEOPLE = Map.of("tutorId", "TUTOR", "studentId", "STUDENT");

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", length = 32, nullable = false, updatable = false)
  private AnomalyKind kind;

  @Column(name = "subject_id", nullable = false, updatable = false)
  private UUID subjectId;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", length = 8, nullable = false, updatable = false)
  private Severity severity;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "detail", updatable = false)
  private Map<String, String> detail;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private AnomalyStatus status;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolution_reason", length = MAX_REASON_LENGTH)
  private String resolutionReason;

  @Column(name = "detected_at", nullable = false, updatable = false)
  private Instant detectedAt;

  protected Anomaly() {
    // Required by JPA
  }

  /**
   * An anomaly that was just detected, open.
   *
   * @param detail the people involved and the figures behind it; may be empty
   */
  public static Anomaly detected(
      UUID id,
      String tenantId,
      AnomalyKind kind,
      UUID subjectId,
      Severity severity,
      Map<String, String> detail,
      Instant detectedAt) {
    Anomaly anomaly = new Anomaly();
    anomaly.id = Objects.requireNonNull(id, "id must not be null");
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("An anomaly belongs to a university");
    }
    anomaly.tenantId = tenantId;
    anomaly.kind = Objects.requireNonNull(kind, "kind must not be null");
    anomaly.subjectId = Objects.requireNonNull(subjectId, "subjectId must not be null");
    anomaly.severity = Objects.requireNonNull(severity, "severity must not be null");
    anomaly.detail = detail == null || detail.isEmpty() ? null : Map.copyOf(detail);
    anomaly.status = AnomalyStatus.OPEN;
    anomaly.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt must not be null");
    return anomaly;
  }

  /**
   * The identifier of the alert that one session raises of one kind in one university.
   *
   * <p>It is derived, not random, so that the same event delivered twice finds the alert already
   * there: the primary key refuses the second one even if both are handled at the same moment.
   */
  public static UUID idFor(String tenantId, AnomalyKind kind, UUID sessionId) {
    String key = tenantId + "|" + kind.name() + "|" + sessionId;
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * A coordinator decides this is not a problem.
   *
   * @throws AnomalyAlreadyResolved when it was decided before
   * @throws InvalidDismissalReason when the reason is blank or longer than {@link
   *     #MAX_REASON_LENGTH}
   */
  public void dismiss(UUID coordinatorId, String reason, Instant at) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(at, "at must not be null");
    if (status != AnomalyStatus.OPEN) {
      throw new AnomalyAlreadyResolved();
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidDismissalReason("A reason is required to dismiss an anomaly");
    }
    String cleaned = reason.strip();
    if (cleaned.length() > MAX_REASON_LENGTH) {
      throw new InvalidDismissalReason(
          "The reason has at most " + MAX_REASON_LENGTH + " characters");
    }
    this.status = AnomalyStatus.DISMISSED;
    this.resolvedBy = coordinatorId;
    this.resolvedAt = at;
    this.resolutionReason = cleaned;
  }

  /** The people this anomaly involves, with the role each had: the subject and whoever the detail names. */
  public Map<UUID, String> involvedPeople() {
    Map<UUID, String> people = new LinkedHashMap<>();
    getDetail().forEach(
        (name, value) -> {
          String role = PEOPLE.get(name);
          if (role != null) {
            try {
              people.putIfAbsent(UUID.fromString(value), role);
            } catch (IllegalArgumentException notAnId) {
              // A detail that does not hold an identifier names nobody.
            }
          }
        });
    people.putIfAbsent(subjectId, "TUTOR");
    return people;
  }

  /** What was found, in a sentence a coordinator can read in a list. */
  public String description() {
    Map<String, String> facts = getDetail();
    return switch (kind) {
      case REPEATED_PAIR ->
          "The same tutor and student completed %s sessions in the last %s days, more than the %s that is usual"
              .formatted(
                  facts.getOrDefault("sessions", "several"),
                  facts.getOrDefault("periodDays", "few"),
                  facts.getOrDefault("threshold", "expected"));
      case SHORT_DURATION ->
          "A completed session lasted %s of the %s minutes that were booked"
              .formatted(
                  facts.getOrDefault("actualMinutes", "few"),
                  facts.getOrDefault("bookedMinutes", "booked"));
      case FAILED_PRESENCE -> "A session ended without every participant confirming their presence";
      case UNUSUAL_FREQUENCY -> "A tutor teaches far more often than is usual";
      case NO_BOARD_ACTIVITY -> "A completed session ended with nothing drawn on its whiteboard";
    };
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public AnomalyKind getKind() {
    return kind;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public Severity getSeverity() {
    return severity;
  }

  public Map<String, String> getDetail() {
    return detail == null ? Map.of() : detail;
  }

  public AnomalyStatus getStatus() {
    return status;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public String getResolutionReason() {
    return resolutionReason;
  }

  public Instant getDetectedAt() {
    return detectedAt;
  }
}
