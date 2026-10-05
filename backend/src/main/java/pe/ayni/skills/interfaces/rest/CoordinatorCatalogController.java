package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.CatalogUsageQuery;
import pe.ayni.skills.application.MergeCatalogItemsUseCase;
import pe.ayni.skills.application.RetireCatalogItemUseCase;

/**
 * What a coordinator does to keep the catalogue clean: see how much an item is used, retire it, join
 * it to another.
 *
 * <p>Every endpoint answers for the coordinator making the request, read from {@link CurrentUser},
 * and the use case checks that they are one.
 */
@RestController
@RequestMapping("/api/v1/coordinator/catalog")
@Validated
@Tag(name = "Coordinator catalogue", description = "Keeping the catalogue clean")
class CoordinatorCatalogController {

  private final CatalogUsageQuery usage;
  private final RetireCatalogItemUseCase retireItem;
  private final MergeCatalogItemsUseCase mergeItems;

  CoordinatorCatalogController(
      CatalogUsageQuery usage,
      RetireCatalogItemUseCase retireItem,
      MergeCatalogItemsUseCase mergeItems) {
    this.usage = usage;
    this.retireItem = retireItem;
    this.mergeItems = mergeItems;
  }

  @GetMapping("/{id}/usage")
  @Operation(
      summary = "How much a catalogue item is used",
      description =
          """
          US44: how many tutors offer the item and how many sessions were taught on it, which is \
          what a moderator reviews the catalogue by. For a global tool the numbers count every \
          university, because retiring or joining it affects all of them; for a course they are \
          the university's own. They are counts, never people. The number of tutors is the one a \
          retirement will ask to confirm.

          The sessions are counted since skills started recording them: the ones completed before \
          that are not included.
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
      description = "The item and its use",
      content = @Content(schema = @Schema(implementation = CatalogUsageResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The item does not exist, or is a course of another university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  CatalogUsageResponse usage(@Parameter(description = "Identifier of the item") @PathVariable UUID id) {
    UUID coordinatorId = CurrentUser.require();
    return CatalogUsageResponse.of(usage.of(coordinatorId, id));
  }

  @PostMapping("/{id}/retire")
  @Operation(
      summary = "Retires a catalogue item",
      description =
          """
          US44: the item can no longer be offered and the search stops finding it. Every tutor who \
          offered it has the offer withdrawn, and the sessions already booked stand.

          The moderator first reads how many tutors are affected in the usage of the item, and \
          confirms by sending that number as confirmedTutors. If it is no longer the number of \
          tutors who offer the item, nothing is retired and the conflict says the number as it is \
          now. Offers still waiting for a review are not counted: they can no longer be approved, \
          and the coordinator rejects them.

          A global tool is retired for every university.
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
      description = "The item was retired",
      content = @Content(schema = @Schema(implementation = RetirementResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The confirmation is missing or negative, or the body cannot be read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "The item does not exist, or is a course of another university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The item is already retired, or the number of tutors changed since it was read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  RetirementResponse retire(
      @Parameter(description = "Identifier of the item") @PathVariable UUID id,
      @Valid @RequestBody RetireCatalogItemRequest request) {
    UUID coordinatorId = CurrentUser.require();
    return RetirementResponse.of(retireItem.execute(coordinatorId, id, request.confirmedTutors()));
  }

  @PostMapping("/{id}/merge")
  @Operation(
      summary = "Joins a duplicate item to the one that stays",
      description =
          """
          US44: two items that are the same skill become one. The item in the path is the duplicate           and is retired; intoCatalogItemId is the one that stays.

          Everything the duplicate had moves to the one that stays: the offers and accreditations of           its tutors keep their status, and an enabled tutor keeps being found in the search. A tutor           who already held both keeps the one on the item that stays and has the other withdrawn. The           learning interests, the proposals that ended in the duplicate and the sessions counted on           it follow. The sessions already booked stand.

          The two must be the same kind of thing: two global tools, or two courses of the           university. A course is never joined to a tool.
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
      description = "The items were joined",
      content = @Content(schema = @Schema(implementation = MergeResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "The same item twice, a course with a tool, the item that would stay is retired, or the body"
              + " cannot be read",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The person asking is not a coordinator",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "404",
      description = "An item does not exist, or is a course of another university",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "409",
      description = "The duplicate is already retired",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  MergeResponse merge(
      @Parameter(description = "Identifier of the duplicate, which is retired") @PathVariable UUID id,
      @Valid @RequestBody MergeCatalogItemsRequest request) {
    UUID coordinatorId = CurrentUser.require();
    return MergeResponse.of(mergeItems.execute(coordinatorId, id, request.intoCatalogItemId()));
  }
}
