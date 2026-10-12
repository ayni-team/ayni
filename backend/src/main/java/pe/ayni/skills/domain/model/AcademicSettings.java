package pe.ayni.skills.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * What a university decided about how its courses are taught: today, the minimum grade.
 *
 * <p>Read-only here. The row is written with an upsert in the repository, so that two coordinators
 * saving the first value at once do not collide on the key.
 */
@Entity
@Immutable
@Table(schema = "skills", name = "academic_settings")
public class AcademicSettings {

  /** The highest grade of the scale the universities grade in. */
  public static final BigDecimal HIGHEST_GRADE = new BigDecimal("20.00");

  @Id
  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "minimum_teaching_grade", precision = 4, scale = 2, nullable = false)
  private BigDecimal minimumTeachingGrade;

  @Column(name = "updated_by", nullable = false)
  private UUID updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AcademicSettings() {
    // Required by JPA
  }

  public String getTenantId() {
    return tenantId;
  }

  public BigDecimal getMinimumTeachingGrade() {
    return minimumTeachingGrade;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
