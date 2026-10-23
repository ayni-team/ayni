package pe.ayni.analytics.domain.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Key of {@link UncoveredDemand}: one row per university, course and day. */
public class UncoveredDemandId implements Serializable {

  private String tenantId;
  private UUID catalogItemId;
  private LocalDate day;

  public UncoveredDemandId() {}

  public UncoveredDemandId(String tenantId, UUID catalogItemId, LocalDate day) {
    this.tenantId = tenantId;
    this.catalogItemId = catalogItemId;
    this.day = day;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof UncoveredDemandId other)) {
      return false;
    }
    return Objects.equals(tenantId, other.tenantId)
        && Objects.equals(catalogItemId, other.catalogItemId)
        && Objects.equals(day, other.day);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, catalogItemId, day);
  }
}
