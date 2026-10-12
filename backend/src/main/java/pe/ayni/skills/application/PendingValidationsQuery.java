package pe.ayni.skills.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.EvidenceFile;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.domain.model.ValidationStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.EvidenceFileRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/**
 * US16: the coordinator's queue, the submissions of their university that still wait, oldest first.
 *
 * <p>Oldest first because a student has been waiting since then. A page costs one read of the
 * skills, one of the items and one of the files, whatever its size; only the people are asked one
 * by one, since identity answers for a single user at a time.
 */
@Service
public class PendingValidationsQuery {

  private final CoordinatorGuard coordinators;
  private final ValidationRequestRepository requests;
  private final OfferedSkillRepository offeredSkills;
  private final CatalogItemRepository catalogItems;
  private final EvidenceFileRepository evidenceFiles;
  private final IdentityApi identity;

  PendingValidationsQuery(
      CoordinatorGuard coordinators,
      ValidationRequestRepository requests,
      OfferedSkillRepository offeredSkills,
      CatalogItemRepository catalogItems,
      EvidenceFileRepository evidenceFiles,
      IdentityApi identity) {
    this.coordinators = coordinators;
    this.requests = requests;
    this.offeredSkills = offeredSkills;
    this.catalogItems = catalogItems;
    this.evidenceFiles = evidenceFiles;
    this.identity = identity;
  }

  /** A submission waiting for a decision, with everything the coordinator needs to take it. */
  public record PendingValidation(
      ValidationRequest request, CatalogItem item, UserView tutor, List<EvidenceFile> files) {}

  /**
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person asking is not a coordinator
   */
  @Transactional(readOnly = true)
  public Page<PendingValidation> page(UUID coordinatorId, int page, int size) {
    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    Page<ValidationRequest> found =
        requests.findByTenantIdAndStatus(
            tenantId, ValidationStatus.SUBMITTED, PageRequest.of(page, size, Sort.by("createdAt", "id")));
    if (found.isEmpty()) {
      return found.map(request -> null);
    }

    List<UUID> requestIds = found.getContent().stream().map(ValidationRequest::getId).toList();
    Map<UUID, OfferedSkill> skills =
        offeredSkills
            .findAllById(found.getContent().stream().map(ValidationRequest::getOfferedSkillId).toList())
            .stream()
            .collect(Collectors.toMap(OfferedSkill::getId, Function.identity()));
    Map<UUID, CatalogItem> items =
        catalogItems
            .findAllById(skills.values().stream().map(OfferedSkill::getCatalogItemId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
    Map<UUID, List<EvidenceFile>> files =
        evidenceFiles.findByTenantIdAndValidationRequestIdIn(tenantId, requestIds).stream()
            .collect(Collectors.groupingBy(EvidenceFile::getValidationRequestId));
    Map<UUID, UserView> tutors =
        skills.values().stream()
            .map(OfferedSkill::getTutorId)
            .distinct()
            .collect(Collectors.toMap(Function.identity(), identity::requireUser));

    return found.map(
        request -> {
          OfferedSkill skill = skills.get(request.getOfferedSkillId());
          return new PendingValidation(
              request,
              items.get(skill.getCatalogItemId()),
              tutors.get(skill.getTutorId()),
              files.getOrDefault(request.getId(), List.of()));
        });
  }
}
