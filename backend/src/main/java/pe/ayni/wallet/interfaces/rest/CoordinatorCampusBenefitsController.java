package pe.ayni.wallet.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.ayni.wallet.application.CampusBenefitCatalog;

/**
 * What a coordinator does with the campus benefits of their university.
 *
 * <p>These two endpoints live under {@code /api/v1/coordinator}: access control gives a role to each
 * group of routes, and everything outside that group is for students (ADR 0007). The catalogue
 * still checks the role itself, because a route says what kind of person may call and the use case
 * is where it matters.
 */
@RestController
@RequestMapping("/api/v1/coordinator/benefits")
@Validated
@Tag(name = "Coordinator benefits", description = "Campus benefits a university offers for earned credits")
class CoordinatorCampusBenefitsController {

  private final CampusBenefitCatalog benefits;

  CoordinatorCampusBenefitsController(CampusBenefitCatalog benefits) {
    this.benefits = benefits;
  }

  @PostMapping
  @Operation(summary = "Create a campus benefit (university coordinator only)")
  CampusBenefitResponse createBenefit(@Valid @RequestBody CampusBenefitRequest request) {
    return CampusBenefitResponse.of(
        benefits.create(
            request.name(), request.description(), request.creditsCost(), request.active()));
  }

  @PutMapping("/{benefitId}")
  @Operation(summary = "Update or deactivate a campus benefit (university coordinator only)")
  CampusBenefitResponse updateBenefit(
      @PathVariable UUID benefitId, @Valid @RequestBody CampusBenefitRequest request) {
    return CampusBenefitResponse.of(
        benefits.update(
            benefitId,
            request.name(),
            request.description(),
            request.creditsCost(),
            request.active()));
  }
}
