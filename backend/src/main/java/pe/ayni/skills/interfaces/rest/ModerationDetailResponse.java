package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import pe.ayni.skills.application.ModerationQuery.ModerationDetail;

/**
 * One proposal and the catalogue items it looks like, so the moderator can tell whether to approve
 * it, join it to one of them or reject it.
 *
 * @param similar empty once the proposal was resolved
 */
@Schema(name = "ModerationProposalDetail", description = "A proposal and what the catalogue has that looks like it")
public record ModerationDetailResponse(
    ModerationProposalResponse proposal, List<CatalogItemResponse> similar) {

  static ModerationDetailResponse of(ModerationDetail detail) {
    return new ModerationDetailResponse(
        ModerationProposalResponse.of(detail.item()),
        detail.similar().stream().map(CatalogItemResponse::of).toList());
  }
}
