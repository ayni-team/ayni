package pe.ayni.skills.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.ValidationRequest;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.ValidationRequestRepository;

/**
 * US18, scenario 1: everything a tutor offers or ever offered, each with where it stands.
 *
 * <p>Withdrawn skills are listed too: the tutor needs to see them to offer one again.
 */
@Service
public class TutorSkillsQuery {

  private final OfferedSkillRepository offeredSkills;
  private final CatalogItemRepository catalogItems;
  private final ValidationRequestRepository requests;

  TutorSkillsQuery(
      OfferedSkillRepository offeredSkills,
      CatalogItemRepository catalogItems,
      ValidationRequestRepository requests) {
    this.offeredSkills = offeredSkills;
    this.catalogItems = catalogItems;
    this.requests = requests;
  }

  /**
   * A skill of the tutor next to the catalogue item it is about.
   *
   * @param latestReview the last submission of evidence made for the skill, or {@code null} for one
   *     that never needed any, such as a course enabled by its grade
   */
  public record TutorSkill(OfferedSkill skill, CatalogItem item, ValidationRequest latestReview) {}

  /** The tutor's skills sorted by the name of the item, in one read of the items and one of the reviews. */
  @Transactional(readOnly = true)
  public List<TutorSkill> of(UUID tutorId) {
    String tenantId = TenantContext.require();

    List<OfferedSkill> skills = offeredSkills.findByTenantIdAndTutorId(tenantId, tutorId);
    if (skills.isEmpty()) {
      return List.of();
    }
    Map<UUID, CatalogItem> items =
        catalogItems.findAllById(skills.stream().map(OfferedSkill::getCatalogItemId).toList()).stream()
            .collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
    // A tutor whose evidence was rejected submits again: only the last submission says where the
    // skill stands now, and the earlier ones stay in the table as history.
    Map<UUID, ValidationRequest> latest =
        requests
            .findByTenantIdAndOfferedSkillIdIn(tenantId, skills.stream().map(OfferedSkill::getId).toList())
            .stream()
            .collect(
                Collectors.toMap(
                    ValidationRequest::getOfferedSkillId,
                    Function.identity(),
                    BinaryOperator.maxBy(Comparator.comparing(ValidationRequest::getCreatedAt))));

    return skills.stream()
        .map(skill -> new TutorSkill(skill, items.get(skill.getCatalogItemId()), latest.get(skill.getId())))
        .sorted(Comparator.comparing((TutorSkill tutorSkill) -> tutorSkill.item().getName()))
        .toList();
  }
}
