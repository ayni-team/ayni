package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import pe.ayni.skills.domain.model.Category;

/** A category of the catalogue, as a student picks one to propose a tool in. */
@Schema(name = "Category", description = "A group the catalogue items are organised under")
public record CategoryResponse(
    @Schema(description = "What a proposal sends as categoryId and the catalogue filters by as category.",
        example = "a0000000-0000-4000-8000-000000000001")
    UUID id,
    @Schema(example = "Technology") String name) {

  static CategoryResponse of(Category category) {
    return new CategoryResponse(category.getId(), category.getName());
  }
}
