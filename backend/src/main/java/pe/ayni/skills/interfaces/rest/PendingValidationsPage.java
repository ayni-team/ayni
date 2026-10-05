package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;
import pe.ayni.skills.application.PendingValidationsQuery;

/**
 * A page of the coordinator's queue.
 *
 * <p>Described with its own fields rather than returned as a Spring {@code Page}, the same way the
 * catalogue and the wallet do.
 */
@Schema(name = "PendingValidationsPage", description = "Evidence waiting for a decision, oldest first")
public record PendingValidationsPage(
    List<PendingValidationResponse> items,
    @Schema(example = "0") int page,
    @Schema(example = "20") int size,
    @Schema(example = "3") long totalElements,
    @Schema(example = "1") int totalPages) {

  static PendingValidationsPage of(Page<PendingValidationsQuery.PendingValidation> found) {
    return new PendingValidationsPage(
        found.getContent().stream().map(PendingValidationResponse::of).toList(),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }
}
