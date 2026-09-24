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
import pe.ayni.skills.CatalogScope;

/**
 * Something a tutor can offer: a university course or a global tool, told apart by {@link
 * #scope}.
 *
 * <p>Global tools ship with Ayni and are the same everywhere; university courses belong to the
 * university that teaches them and carry the course code its academic system reports. A global
 * item can never belong to a university, which would quietly make it invisible to every other
 * one: {@link #validateInvariants()} enforces the same rule {@code
 * ck_catalog_items_tenant_matches_scope} enforces in the database.
 */
@Entity
@Table(schema = "skills", name = "catalog_items")
public class CatalogItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", length = 16, nullable = false, updatable = false)
  private CatalogScope scope;

  /** {@code null} when {@link #scope} is {@link CatalogScope#GLOBAL}. */
  @Column(name = "tenant_id", length = 32, updatable = false)
  private String tenantId;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(name = "name", length = 160, nullable = false)
  private String name;

  @Column(name = "description", length = 500)
  private String description;

  /** {@code null} for global items, required for university ones. */
  @Column(name = "course_code", length = 32, updatable = false)
  private String courseCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private CatalogItemStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CatalogItem() {
    // Required by JPA
  }

  public CatalogItem(
      UUID id,
      CatalogScope scope,
      String tenantId,
      UUID categoryId,
      String name,
      String description,
      String courseCode,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.scope = Objects.requireNonNull(scope, "scope must not be null");
    this.tenantId = tenantId;
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    this.name = requireNonBlank(name, "name");
    this.description = description;
    this.courseCode = courseCode;
    this.status = CatalogItemStatus.ACTIVE;
    this.createdAt = Objects.requireNonNull(now, "now must not be null");

    validateInvariants();
  }

  private static String requireNonBlank(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private void validateInvariants() {
    boolean isGlobal = this.scope == CatalogScope.GLOBAL;
    if (isGlobal == (this.tenantId != null)) {
      throw new IllegalArgumentException(
          "tenantId must be null for GLOBAL items and set for UNIVERSITY items");
    }
    if (!isGlobal && (this.courseCode == null || this.courseCode.isBlank())) {
      throw new IllegalArgumentException("courseCode is required for UNIVERSITY items");
    }
  }

  /** Retired items stop being offerable, but keep the history that already points at them. */
  public void retire() {
    this.status = CatalogItemStatus.RETIRED;
  }

  public boolean isActive() {
    return this.status == CatalogItemStatus.ACTIVE;
  }

  /** Whether a student of {@code tenantId} may see this item: it is global or their own. */
  public boolean isVisibleTo(String tenantId) {
    return this.scope == CatalogScope.GLOBAL || Objects.equals(this.tenantId, tenantId);
  }

  public UUID getId() {
    return id;
  }

  public CatalogScope getScope() {
    return scope;
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getCourseCode() {
    return courseCode;
  }

  public CatalogItemStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
