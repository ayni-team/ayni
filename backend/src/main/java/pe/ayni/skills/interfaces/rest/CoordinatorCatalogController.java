package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.skills.application.CatalogUsageQuery;

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

  CoordinatorCatalogController(CatalogUsageQuery usage) {
    this.usage = usage;
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
}
