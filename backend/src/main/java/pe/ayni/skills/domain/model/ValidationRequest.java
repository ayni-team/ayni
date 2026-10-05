package pe.ayni.skills.domain.model;

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
 * One submission of evidence for a skill, and what the university decided about it.
 *
 * <p>A request is decided once. A tutor whose evidence was rejected does not reopen it: they submit
 * again and get a new request, so the reason given the first time stays readable. That is also why
 * the review is recorded here and not on the skill, which only knows where it stands now.
 */
@Entity
@Table(schema = "skills", name = "validation_requests")
public class ValidationRequest {

  static final int MAX_NOTE_LENGTH = 1000;
  static final int MAX_REASON_LENGTH = 500;

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "offered_skill_id", nullable = false, updatable = false)
  private UUID offeredSkillId;

  @Column(name = "student_note", length = MAX_NOTE_LENGTH, updatable = false)
  private String studentNote;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private ValidationStatus status;

  /** The coordinator who decided. {@code null} while the request waits. */
  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "decision_reason", length = MAX_REASON_LENGTH)
  private String decisionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected ValidationRequest() {
    // Required by JPA
  }

  private ValidationRequest(
      UUID id, String tenantId, UUID offeredSkillId, String studentNote, Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.offeredSkillId = Objects.requireNonNull(offeredSkillId, "offeredSkillId must not be null");
    this.studentNote = studentNote;
    this.status = ValidationStatus.SUBMITTED;
    this.createdAt = Objects.requireNonNull(now, "now must not be null");
  }

  /**
   * Submits evidence for review.
   *
   * @param studentNote what the student wants the reviewer to know; {@code null} or blank for none
   * @throws SkillsRuleViolation when the note is longer than the column holds
   */
  public static ValidationRequest submit(
      UUID id, String tenantId, UUID offeredSkillId, String studentNote, Instant now) {
    return new ValidationRequest(
        id, tenantId, offeredSkillId, blankToNull(studentNote, MAX_NOTE_LENGTH, "note"), now);
  }

  /**
   * Accepts the evidence.
   *
   * @param reason why, if the reviewer wants to say; {@code null} or blank for none
   * @throws SkillsStateConflict when the request was already decided
   */
  public void approve(UUID reviewerId, String reason, Instant now) {
    requireWaiting();
    String normalised = blankToNull(reason, MAX_REASON_LENGTH, "reason");
    resolve(ValidationStatus.APPROVED, reviewerId, normalised, now);
  }

  /**
   * Refuses the evidence. The student reads the reason, so it is required.
   *
   * @throws SkillsRuleViolation when the reason is missing or longer than the column holds
   * @throws SkillsStateConflict when the request was already decided
   */
  public void reject(UUID reviewerId, String reason, Instant now) {
    requireWaiting();
    String normalised = blankToNull(reason, MAX_REASON_LENGTH, "reason");
    if (normalised == null) {
      throw new SkillsRuleViolation("a rejection needs a reason the student can read");
    }
    resolve(ValidationStatus.REJECTED, reviewerId, normalised, now);
  }

  public boolean isWaiting() {
    return this.status == ValidationStatus.SUBMITTED;
  }

  private void requireWaiting() {
    if (!isWaiting()) {
      throw new SkillsStateConflict("this request was already decided");
    }
  }

  private void resolve(ValidationStatus outcome, UUID reviewerId, String reason, Instant now) {
    this.reviewedBy = Objects.requireNonNull(reviewerId, "reviewerId must not be null");
    this.reviewedAt = Objects.requireNonNull(now, "now must not be null");
    this.decisionReason = reason;
    this.status = outcome;
  }

  private static String blankToNull(String text, int maxLength, String field) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String stripped = text.strip();
    if (stripped.length() > maxLength) {
      throw new SkillsRuleViolation("the %s can have at most %d characters".formatted(field, maxLength));
    }
    return stripped;
  }

  public UUID getId() {
    return id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getOfferedSkillId() {
    return offeredSkillId;
  }

  public String getStudentNote() {
    return studentNote;
  }

  public ValidationStatus getStatus() {
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}
