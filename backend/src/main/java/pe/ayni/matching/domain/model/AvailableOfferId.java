package pe.ayni.matching.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Identifies one offer: a block of a university, for one of the courses its tutor teaches. */
public class AvailableOfferId implements Serializable {

  private static final long serialVersionUID = 1L;

  private String tenantId;
  private UUID blockId;
  private UUID catalogItemId;

  protected AvailableOfferId() {
    // Required by JPA
  }

  public AvailableOfferId(String tenantId, UUID blockId, UUID catalogItemId) {
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.blockId = Objects.requireNonNull(blockId, "blockId must not be null");
    this.catalogItemId = Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
  }

  public String tenantId() {
    return tenantId;
  }

  public UUID blockId() {
    return blockId;
  }

  public UUID catalogItemId() {
    return catalogItemId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AvailableOfferId that)) {
      return false;
    }
    return Objects.equals(tenantId, that.tenantId)
        && Objects.equals(blockId, that.blockId)
        && Objects.equals(catalogItemId, that.catalogItemId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenantId, blockId, catalogItemId);
  }

  @Override
  public String toString() {
    return tenantId + "/" + blockId + "/" + catalogItemId;
  }
}
