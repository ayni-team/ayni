package pe.ayni.analytics.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Key of {@link CountedEvent}. */
public class CountedEventId implements Serializable {

  private String tenantId;
  private String kind;
  private UUID sourceId;

  public CountedEventId() {}

  public CountedEventId(String tenantId, String kind, UUID sourceId) {
    this.tenantId = tenantId;
    this.kind = kind;
    this.sourceId = sourceId;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof CountedEventId other)) {
      return false;
    }
    return Objects.equals(tenantId, other.tenantId)
        && Objects.equals(kind, other.kind)
        && Objects.equals(sourceId, other.sourceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, kind, sourceId);
  }
}
