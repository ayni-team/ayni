package pe.ayni.wallet.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "wallet", name = "campus_benefits")
public class CampusBenefit {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Column(name = "name", length = 120, nullable = false)
  private String name;

  @Column(name = "description", length = 500, nullable = false)
  private String description;

  @Column(name = "credits_cost", nullable = false)
  private int creditsCost;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CampusBenefit() {
    // Required by JPA
  }

  private CampusBenefit(
      UUID id,
      String tenantId,
      String name,
      String description,
      int creditsCost,
      boolean active,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.createdAt = Objects.requireNonNull(now, "now must not be null");
    update(name, description, creditsCost, active, now);
  }

  public static CampusBenefit create(
      UUID id,
      String tenantId,
      String name,
      String description,
      int creditsCost,
      boolean active,
      Instant now) {
    return new CampusBenefit(id, tenantId, name, description, creditsCost, active, now);
  }

  public void update(
      String name, String description, int creditsCost, boolean active, Instant now) {
    this.name = requiredText(name, 120, "name");
    this.description = requiredText(description, 500, "description");
    if (creditsCost < 1) {
      throw new CreditRuleViolation("A campus benefit must cost at least one earned credit");
    }
    this.creditsCost = creditsCost;
    this.active = active;
    this.updatedAt = Objects.requireNonNull(now, "now must not be null");
  }

  private static String requiredText(String value, int maxLength, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    String trimmed = value.trim();
    if (trimmed.isEmpty() || trimmed.length() > maxLength) {
      throw new CreditRuleViolation(
          "A campus benefit " + field + " must contain 1 to " + maxLength + " characters");
    }
    return trimmed;
  }

  public UUID id() {
    return id;
  }

  public String tenantId() {
    return tenantId;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public int creditsCost() {
    return creditsCost;
  }

  public boolean active() {
    return active;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
