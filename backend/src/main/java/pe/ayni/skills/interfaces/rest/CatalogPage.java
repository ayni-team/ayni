package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;
import pe.ayni.skills.domain.model.CatalogItem;

/**
 * A page of the catalogue.
 *
 * <p>Described with its own fields rather than returned as a Spring {@code Page}, whose shape is an
 * implementation detail of the framework, the same way wallet answers its movement history.
 */
@Schema(name = "CatalogPage", description = "Catalogue items, sorted by name")
public record CatalogPage(
    List<CatalogItemResponse> items,
    @Schema(example = "0") int page,
    @Schema(example = "20") int size,
    @Schema(example = "8") long totalElements,
    @Schema(example = "1") int totalPages) {

  static CatalogPage of(Page<CatalogItem> found) {
    return new CatalogPage(
        found.getContent().stream().map(CatalogItemResponse::of).toList(),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }
}
