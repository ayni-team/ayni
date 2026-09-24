package pe.ayni.skills.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What a student wants to receive help with, catalogue item by catalogue item.
 *
 * <p>Drives the recommendations shown on the portal. Declaring one twice for the same item is not
 * an error, just a fact the database already knows: {@code
 * uq_learning_interests_student_item} makes a repeat insert redundant rather than wrong, which is
 * why nothing here refuses it.
 */
@Entity
@Table(schema = "skills", name = "learning_interests")
public class LearningInterest {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "catalog_item_id", nullable = false, updatable = false)
  private UUID catalogItemId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected LearningInterest() {
    // Required by JPA
  }

  public LearningInterest(UUID id, String tenantId, UUID studentId, UUID catalogItemId, Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
    this.catalogItemId = Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    this.createdAt = Objects.requireNonNull(now, "now must not be null");
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

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
