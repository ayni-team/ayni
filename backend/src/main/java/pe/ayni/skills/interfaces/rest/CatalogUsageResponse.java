package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import pe.ayni.skills.application.CatalogUsageQuery.CatalogUsage;

/**
 * A catalogue item and how much it is used, which is what a moderator reviews the catalogue by.
 *
 * @param tutorsOffering tutors whose offer of the item is enabled right now, in every university
 *     when the item is global. It is the number a retirement affects
 * @param sessionsTaught verified sessions taught on the item since skills started counting them
 */
@Schema(name = "CatalogUsage", description = "A catalogue item and how much it is used")
public record CatalogUsageResponse(
    Item item,
    @Schema(example = "12") long tutorsOffering,
    @Schema(example = "48") long sessionsTaught) {

  /** The item, with its status: a moderator also reviews what was already retired. */
  @Schema(name = "CatalogUsageItem")
  public record Item(
      @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID id,
      @Schema(example = "Figma") String name,
      @Schema(allowableValues = {"GLOBAL", "UNIVERSITY"}, example = "GLOBAL") String scope,
      @Schema(allowableValues = {"ACTIVE", "RETIRED"}, example = "ACTIVE") String status) {}

  static CatalogUsageResponse of(CatalogUsage usage) {
    return new CatalogUsageResponse(
        new Item(
            usage.item().getId(),
            usage.item().getName(),
            usage.item().getScope().name(),
            usage.item().getStatus().name()),
        usage.tutorsOffering(),
        usage.sessionsTaught());
  }
}
