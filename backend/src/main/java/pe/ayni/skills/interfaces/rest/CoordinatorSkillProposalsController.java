package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.ModerationQuery;
import pe.ayni.skills.application.ResolveProposalUseCase;
import pe.ayni.skills.domain.model.ProposalStatus;

/**
 * What a coordinator does with the tools their students propose: read the queue and the history.
 *
 * <p>Every endpoint answers for the coordinator making the request, read from {@link CurrentUser},
 * and the use case checks that they are one, like the evidence queue does.
 */
@RestController
@RequestMapping("/api/v1/coordinator/skill-proposals")
@Validated
@Tag(name = "Coordinator skill proposals", description = "Moderation of the tools students propose")
class CoordinatorSkillProposalsController {

  private final ModerationQuery moderation;
  private final ResolveProposalUseCase resolveProposal;

  CoordinatorSkillProposalsController(
      ModerationQuery moderation, ResolveProposalUseCase resolveProposal) {
    this.moderation = moderation;
    this.resolveProposal = resolveProposal;
  }

  @GetMapping
  @Operation(
      summary = "The proposals of the university, waiting or already resolved",
      description =
          """
          US43: with no status, the queue of proposals nobody resolved yet, oldest first, each with \
          its name, category, who proposed it and since when it waits. With a status of APPROVED, \
          MERGED or REJECTED, the history: the proposals already resolved, newest first, each \
          saying who resolved it, when and how.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Coordinator making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "One page of proposals",
      content = @Content(schema = @Schema(implementation = ModerationProposalsPage.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The status is not one of PROPOSED, APPROVED, MERGED or REJECTED",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ModerationProposalsPage proposals(
      @Parameter(description = "Which proposals: the ones waiting by default")
          @RequestParam(defaultValue = "PROPOSED")
          ProposalStatus status,
      @Parameter(description = "Page number, starting at zero") @RequestParam(defaultValue = "0")
          @Min(0)
          int page,
      @Parameter(description = "Proposals per page") @RequestParam(defaultValue = "20") @Min(1)
          @Max(100)
          int size) {
    UUID coordinatorId = CurrentUser.require();
    return ModerationProposalsPage.of(moderation.page(coordinatorId, status, page, size));
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "One proposal, with the catalogue items it looks like",
      description =
          """
          US43: the proposal as the list shows it, plus up to five catalogue items that look like \
          its name, which is what the coordinator needs to decide whether it is new or the same \
          skill under another name. Once the proposal was resolved, the list of similar items is \
          empty and the resolution says who decided, when and how.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Coordinator making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "The proposal and the similar items",
      content = @Content(schema = @Schema(implementation = ModerationDetailResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The proposal does not exist in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ModerationDetailResponse proposal(
      @Parameter(description = "Identifier of the proposal") @PathVariable UUID id) {
    UUID coordinatorId = CurrentUser.require();
    return ModerationDetailResponse.of(moderation.detail(coordinatorId, id));
  }

  @PostMapping("/{id}/decision")
  @Operation(
      summary = "Approves, joins or rejects a proposal",
      description =
          """
          US43: APPROVE adds the tool to the catalogue as a global item, available to every \
          university. MERGE joins the proposal to a skill the catalogue already has, given in \
          catalogItemId, and creates nothing: the student reads that item in their proposals. \
          REJECT needs a reason, because the student reads it. The proposal keeps who decided, \
          when and how. A proposal is resolved once.

          Approving a name the catalogue already has is refused with a conflict: join it instead.
          """)
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-Tenant-Id",
      required = true,
      description = "University the request belongs to. Read by TenantFilter",
      schema = @Schema(type = "string", example = "UPC"))
  @Parameter(
      in = ParameterIn.HEADER,
      name = "X-User-Id",
      required = true,
      description = "Coordinator making the request. Read by CurrentUserFilter",
      schema =
          @Schema(type = "string", format = "uuid", example = "22222222-2222-4222-8222-222222222222"))
  @ApiResponse(
      responseCode = "200",
      description = "The decision was recorded",
      content = @Content(schema = @Schema(implementation = ProposalDecisionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "A rejection without a reason, a merge without an item, an item with another decision, a"
              + " retired item, or a body that cannot be read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The proposal, or the item to join it to, is not visible in this university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The proposal was already resolved, or the catalogue already has that name",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ProposalDecisionResponse decide(
      @Parameter(description = "Identifier of the proposal") @PathVariable UUID id,
      @Valid @RequestBody ProposalDecisionRequest request) {
    UUID coordinatorId = CurrentUser.require();
    return ProposalDecisionResponse.of(
        resolveProposal.execute(
            coordinatorId,
            id,
            ResolveProposalUseCase.Decision.valueOf(request.decision().name()),
            request.catalogItemId(),
            request.reason()));
  }
}
