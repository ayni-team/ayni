package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import pe.ayni.skills.domain.model.CatalogItem;

/**
 * A catalogue item as the student sees it.
 *
 * @param courseCode {@code null} for a global item
 */
@Schema(name = "CatalogItem", description = "Something a tutor can offer: a course or a global tool")
public record CatalogItemResponse(
    @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID id,
    @Schema(example = "UNIVERSITY") String scope,
    @Schema(example = "Software Architecture Fundamentals") String name,
    @Schema(nullable = true) String description,
    @Schema(nullable = true, example = "1ASI0657") String courseCode) {

  static CatalogItemResponse of(CatalogItem item) {
    return new CatalogItemResponse(
        item.getId(), item.getScope().name(), item.getName(), item.getDescription(), item.getCourseCode());
  }
}
