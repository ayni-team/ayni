package pe.ayni.skills.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US18, scenario 1: everything a tutor offers or ever offered, each with where it stands.
 *
 * <p>Withdrawn skills are listed too: the tutor needs to see them to offer one again.
 */
@Service
public class TutorSkillsQuery {

  private final OfferedSkillRepository offeredSkills;
  private final CatalogItemRepository catalogItems;

  TutorSkillsQuery(OfferedSkillRepository offeredSkills, CatalogItemRepository catalogItems) {
    this.offeredSkills = offeredSkills;
    this.catalogItems = catalogItems;
  }

  /** A skill of the tutor next to the catalogue item it is about. */
  public record TutorSkill(OfferedSkill skill, CatalogItem item) {}

  /** The tutor's skills sorted by the name of the item, in one read of the items. */
  @Transactional(readOnly = true)
  public List<TutorSkill> of(UUID tutorId) {
    String tenantId = TenantContext.require();

    List<OfferedSkill> skills = offeredSkills.findByTenantIdAndTutorId(tenantId, tutorId);
    Map<UUID, CatalogItem> items =
        catalogItems.findAllById(skills.stream().map(OfferedSkill::getCatalogItemId).toList()).stream()
            .collect(Collectors.toMap(CatalogItem::getId, Function.identity()));

    return skills.stream()
        .map(skill -> new TutorSkill(skill, items.get(skill.getCatalogItemId())))
        .sorted(Comparator.comparing((TutorSkill tutorSkill) -> tutorSkill.item().getName()))
        .toList();
  }
}
