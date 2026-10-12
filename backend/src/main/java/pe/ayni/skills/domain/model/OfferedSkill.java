package pe.ayni.skills.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What a tutor is enabled to teach.
 *
 * <p>A university course reaches {@link OfferedSkillStatus#ENABLED} on its own when the grade
 * clears the university's threshold ({@link #enableByAcademicRecord}, US13). A global tool can
 * only reach it through reviewed evidence, because no academic record can vouch for it; that path
 * is not built by this class yet.
 */
@Entity
@Table(schema = "skills", name = "offered_skills")
public class OfferedSkill {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private UUID tutorId;

  @Column(name = "catalog_item_id", nullable = false, updatable = false)
  private UUID catalogItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private OfferedSkillStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "accreditation_path", length = 24)
  private AccreditationPath accreditationPath;

  /** The grade that enabled it, copied at the time it was checked. {@code null} until then. */
  @Column(name = "accredited_grade", precision = 4, scale = 2)
  private BigDecimal accreditedGrade;

  @Column(name = "enabled_at")
  private Instant enabledAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OfferedSkill() {
    // Required by JPA
  }

  private OfferedSkill(
      UUID id,
      String tenantId,
      UUID tutorId,
      UUID catalogItemId,
      OfferedSkillStatus status,
      AccreditationPath accreditationPath,
      BigDecimal accreditedGrade,
      Instant enabledAt,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.tutorId = Objects.requireNonNull(tutorId, "tutorId must not be null");
    this.catalogItemId = Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    this.status = status;
    this.accreditationPath = accreditationPath;
    this.accreditedGrade = accreditedGrade;
    this.enabledAt = enabledAt;
    this.createdAt = Objects.requireNonNull(now, "now must not be null");
    this.updatedAt = now;
  }

  /**
   * Offers a university course the tutor's academic record already clears.
   *
   * @param grade the grade the academic system reports for this course
   * @param threshold the university's minimum teaching grade
   * @throws SkillsRuleViolation when the grade does not clear the threshold
   */
  public static OfferedSkill enableByAcademicRecord(
      UUID id,
      String tenantId,
      UUID tutorId,
      UUID catalogItemId,
      BigDecimal grade,
      BigDecimal threshold,
      Instant now) {
    Objects.requireNonNull(grade, "grade must not be null");
    Objects.requireNonNull(threshold, "threshold must not be null");
    if (grade.compareTo(threshold) < 0) {
      throw new SkillsRuleViolation(
          "grade %s does not reach the university's threshold %s".formatted(grade, threshold));
    }
    return new OfferedSkill(
        id,
        tenantId,
        tutorId,
        catalogItemId,
        OfferedSkillStatus.ENABLED,
        AccreditationPath.ACADEMIC_RECORD,
        grade,
        now,
        now);
  }

  /**
   * Offers again a course the tutor withdrew, once the academic record still clears the threshold.
   *
   * <p>The accreditation is not carried over: the grade is checked again against today's threshold
   * and copied again, because the threshold may have changed since the first offer.
   *
   * @throws SkillsStateConflict when the skill is not withdrawn, or was not accredited by the
   *     academic record
   * @throws SkillsRuleViolation when the grade no longer clears the threshold
   */
  public void reEnableByAcademicRecord(BigDecimal grade, BigDecimal threshold, Instant now) {
    Objects.requireNonNull(grade, "grade must not be null");
    Objects.requireNonNull(threshold, "threshold must not be null");
    Objects.requireNonNull(now, "now must not be null");
    if (this.status != OfferedSkillStatus.WITHDRAWN
        || this.accreditationPath != AccreditationPath.ACADEMIC_RECORD) {
      throw new SkillsStateConflict("only a withdrawn course can be offered again");
    }
    if (grade.compareTo(threshold) < 0) {
      throw new SkillsRuleViolation(
          "grade %s does not reach the university's threshold %s".formatted(grade, threshold));
    }
    this.status = OfferedSkillStatus.ENABLED;
    this.accreditedGrade = grade;
    this.enabledAt = now;
    this.updatedAt = now;
  }

  /**
   * Stops the tutor from offering this skill; reservations already confirmed are unaffected.
   *
   * @throws SkillsStateConflict when the skill is not enabled
   */
  public void withdraw(Instant now) {
    if (this.status != OfferedSkillStatus.ENABLED) {
      throw new SkillsStateConflict("only an enabled skill can be withdrawn");
    }
    this.status = OfferedSkillStatus.WITHDRAWN;
    this.updatedAt = Objects.requireNonNull(now, "now must not be null");
  }

  public boolean isEnabled() {
    return this.status == OfferedSkillStatus.ENABLED;
  }

  public UUID getId() {
    return id;
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

  public OfferedSkillStatus getStatus() {
    return status;
  }

  public AccreditationPath getAccreditationPath() {
    return accreditationPath;
  }

  public BigDecimal getAccreditedGrade() {
    return accreditedGrade;
  }

  public Instant getEnabledAt() {
    return enabledAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
