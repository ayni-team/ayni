package pe.ayni.analytics.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * How many searches of a course found no hour on a day, in a university: the row analytics keeps
 * of what students ask for and nobody offers.
 *
 * <p>The projection writes these rows with an atomic increment, so two events arriving at once
 * cannot lose one; the entity is only ever read, and Hibernate treats it as immutable. It holds a
 * count and a course, never a person.
 */
@Entity
@Immutable
@IdClass(UncoveredDemandId.class)
@Table(schema = "analytics", name = "uncovered_demand")
public class UncoveredDemand {

  @Id
  @Column(name = "tenant_id", length = 32, nullable = false, updatable = false)
  private String tenantId;

  @Id
  @Column(name = "catalog_item_id", nullable = false, updatable = false)
  private UUID catalogItemId;

  @Id
  @Column(name = "day", nullable = false, updatable = false)
  private LocalDate day;

  @Column(name = "searches", nullable = false, updatable = false)
  private int searches;

  protected UncoveredDemand() {
    // Required by JPA
  }

  public String getTenantId() {
    return tenantId;
  }

  public UUID getCatalogItemId() {
    return catalogItemId;
  }

  public LocalDate getDay() {
    return day;
  }

  public int getSearches() {
    return searches;
  }
}
