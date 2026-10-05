package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import pe.ayni.skills.application.MergeCatalogItemsUseCase.Merge;

/** What joining two items moved to the one that stays. */
@Schema(name = "CatalogMerge", description = "The outcome of joining two catalogue items")
public record MergeResponse(
    @Schema(description = "The duplicate, now retired", example = "b0000000-0000-4000-8000-000000000102")
        UUID removedCatalogItemId,
    @Schema(description = "The item that stays", example = "b0000000-0000-4000-8000-000000000101")
        UUID keptCatalogItemId,
    @Schema(description = "Offers and accreditations that moved to the item that stays", example = "7")
        long offersMoved,
    @Schema(
            description = "Enabled offers of tutors who already held the item that stays, withdrawn",
            example = "1")
        long offersWithdrawn,
    @Schema(example = "4") long interestsMoved,
    @Schema(description = "Interests of students who already had one in the item that stays", example = "2")
        long interestsDropped,
    @Schema(example = "1") long proposalsRepointed,
    @Schema(description = "Sessions now counted on the item that stays", example = "12")
        long sessionsCarriedOver) {

  static MergeResponse of(Merge merge) {
    return new MergeResponse(
        merge.removed().getId(),
        merge.kept().getId(),
        merge.offersMoved(),
        merge.offersWithdrawn(),
        merge.interestsMoved(),
        merge.interestsDropped(),
        merge.proposalsRepointed(),
        merge.sessionsCarriedOver());
  }
}
