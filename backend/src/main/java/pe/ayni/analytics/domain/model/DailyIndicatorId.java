package pe.ayni.analytics.domain.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Key of {@link DailyIndicator}: one row per university per day. */
public class DailyIndicatorId implements Serializable {

  private String tenantId;
  private LocalDate day;

  public DailyIndicatorId() {}

  public DailyIndicatorId(String tenantId, LocalDate day) {
    this.tenantId = tenantId;
    this.day = day;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof DailyIndicatorId other)) {
      return false;
    }
    return Objects.equals(tenantId, other.tenantId) && Objects.equals(day, other.day);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, day);
  }
}
