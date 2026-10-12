package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;
import pe.ayni.skills.application.ModerationQuery.ModerationItem;

/** A page of the moderator's queue or history, described with its own fields like the other pages. */
@Schema(name = "ModerationProposalsPage", description = "Proposals of the university in one status")
public record ModerationProposalsPage(
    List<ModerationProposalResponse> items,
    @Schema(example = "0") int page,
    @Schema(example = "20") int size,
    @Schema(example = "3") long totalElements,
    @Schema(example = "1") int totalPages) {

  static ModerationProposalsPage of(Page<ModerationItem> found) {
    return new ModerationProposalsPage(
        found.getContent().stream().map(ModerationProposalResponse::of).toList(),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }
}
