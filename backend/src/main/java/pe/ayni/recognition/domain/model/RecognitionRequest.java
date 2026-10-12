package pe.ayni.recognition.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A student's request to their university to recognise the hours they taught.
 *
 * <p>The figures are copied when the request is {@linkplain #submit submitted} and never change: the
 * coordinator reads what the student presented, not a number that moves while they review it.
 *
 * <p>Ayni certifies nothing by itself. The request waits for the university's decision.
 */
@Entity
@Table(schema = "recognition", name = "requests")
public class RecognitionRequest {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "total_hours", nullable = false, updatable = false)
  private int totalHours;

  @Column(name = "sessions_count", nullable = false, updatable = false)
  private int sessionsCount;

  /** {@code null} when none of the sessions had a rating. */
  @Column(name = "average_rating", precision = 3, scale = 2, updatable = false)
  private BigDecimal averageRating;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private RequestStatus status;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "decision_reason", length = 1000)
  private String decisionReason;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private Instant submittedAt;

  protected RecognitionRequest() {
    // Required by JPA
  }

  private RecognitionRequest(
      UUID id,
      String tenantId,
      UUID studentId,
      int totalHours,
      int sessionsCount,
      BigDecimal averageRating,
      Instant now) {
    this.id = id;
    this.tenantId = tenantId;
    this.studentId = studentId;
    this.totalHours = totalHours;
    this.sessionsCount = sessionsCount;
    this.averageRating = averageRating;
    this.status = RequestStatus.SUBMITTED;
    this.submittedAt = now;
  }

  /**
   * Builds the request for these sessions, copying the figures from them.
   *
   * <p>The hours are the sum of the sessions' hours, and the average rating is the mean of the ratings
   * there are, to two decimals. The sessions are attached to the request, which the caller saves.
   *
   * @throws IllegalArgumentException when there are no sessions
   */
  public static RecognitionRequest submit(
      UUID id, String tenantId, UUID studentId, List<RequestedSession> sessions, Instant now) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(studentId, "studentId must not be null");
    Objects.requireNonNull(sessions, "sessions must not be null");
    Objects.requireNonNull(now, "now must not be null");
    if (sessions.isEmpty()) {
      throw new IllegalArgumentException("a request needs at least one session");
    }
    int hours = sessions.stream().mapToInt(RequestedSession::getHours).sum();
    List<Integer> ratings = sessions.stream().map(RequestedSession::getStars).filter(Objects::nonNull).toList();
    BigDecimal average =
        ratings.isEmpty()
            ? null
            : BigDecimal.valueOf(ratings.stream().mapToInt(Integer::intValue).sum())
                .divide(BigDecimal.valueOf(ratings.size()), 2, RoundingMode.HALF_UP);
    sessions.forEach(session -> session.attachTo(id));
    return new RecognitionRequest(id, tenantId, studentId, hours, sessions.size(), average, now);
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getStudentId() {
    return studentId;
  }

  public int getTotalHours() {
    return totalHours;
  }

  public int getSessionsCount() {
    return sessionsCount;
  }

  public BigDecimal getAverageRating() {
    return averageRating;
  }

  public RequestStatus getStatus() {
    return status;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }
}
