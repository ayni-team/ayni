package pe.ayni.skills.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.ValidationResolved;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/**
 * US16, scenarios 2 and 3: a coordinator approves or rejects the evidence of a tutor.
 *
 * <p>Approving enables the skill by the reviewed evidence, and the request keeps who decided and
 * when, next to the files that were reviewed. Rejecting needs a reason, because the student reads
 * it and submits again from there.
 *
 * <p>Two events leave from here. {@code ValidationResolved} tells whoever notifies the student what
 * was decided; {@code SkillEnabled} is the one the search projection listens to, so an approved
 * tool becomes findable the same way a course enabled by its grade does.
 */
@Service
public class ResolveValidationUseCase {

  private final CoordinatorGuard coordinators;
  private final ValidationRequestRepository requests;
  private final OfferedSkillRepository offeredSkills;
  private final CatalogItemRepository catalogItems;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  ResolveValidationUseCase(
      CoordinatorGuard coordinators,
      ValidationRequestRepository requests,
      OfferedSkillRepository offeredSkills,
      CatalogItemRepository catalogItems,
      ApplicationEventPublisher events,
      Clock clock) {
    this.coordinators = coordinators;
    this.requests = requests;
    this.offeredSkills = offeredSkills;
    this.catalogItems = catalogItems;
    this.events = events;
    this.clock = clock;
  }

  /** What was decided, to answer the coordinator with. */
  public record Resolution(ValidationRequest request, OfferedSkill skill) {}

  /**
   * @param approved whether the evidence is accepted
   * @param reason why; required to reject, optional to approve
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   * @throws NoSuchElementException when no request has that identifier in this university
   * @throws SkillsStateConflict when the request was already decided
   * @throws SkillsRuleViolation when a rejection comes without a reason
   */
  @Transactional
  public Resolution execute(UUID coordinatorId, UUID requestId, boolean approved, String reason) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(requestId, "requestId must not be null");

    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    // Locked: two coordinators on the same request must not both decide it.
    ValidationRequest request =
        requests
            .lockByTenantIdAndId(tenantId, requestId)
            .orElseThrow(
                () -> new NoSuchElementException("validation request %s not found".formatted(requestId)));
    OfferedSkill skill =
        offeredSkills
            .lockByTenantIdAndId(tenantId, request.getOfferedSkillId())
            .orElseThrow(
                () -> new NoSuchElementException("the skill of request %s not found".formatted(requestId)));

    if (approved) {
      // A tool retired while the evidence waited cannot be enabled: that would put it back in the
      // search. The coordinator can still reject the evidence.
      catalogItems
          .findById(skill.getCatalogItemId())
          .filter(item -> !item.isActive())
          .ifPresent(
              retired -> {
                throw new SkillsStateConflict(
                    "this tool was retired from the catalogue, so its evidence can no longer be approved");
              });
    }

    Instant now = clock.instant();
    if (approved) {
      request.approve(coordinatorId, reason, now);
      skill.approveByReviewedEvidence(now);
    } else {
      request.reject(coordinatorId, reason, now);
      skill.rejectEvidence(now);
    }

    events.publishEvent(
        new ValidationResolved(
            tenantId,
            request.getId(),
            skill.getTutorId(),
            skill.getCatalogItemId(),
            approved,
            request.getDecisionReason(),
            now));
    if (approved) {
      events.publishEvent(new SkillEnabled(tenantId, skill.getTutorId(), skill.getCatalogItemId(), now));
    }

    return new Resolution(request, skill);
  }
}
