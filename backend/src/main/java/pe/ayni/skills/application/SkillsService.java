package pe.ayni.skills.application;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.SkillsApi;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * What the other modules see of skills.
 *
 * <p>It holds no rules of its own: each answer is a read against this module's tables, and this
 * class is the door the other modules are reached through. Matching depends on {@link SkillsApi}
 * and never learns that {@code OfferedSkill} exists, so the inside of skills can be rearranged
 * without anybody else changing.
 */
@Service
public class SkillsService implements SkillsApi {

  private final OfferedSkillRepository offeredSkills;
  private final CatalogItemRepository catalogItems;
  private final DeclareInterestsUseCase declareInterests;

  SkillsService(
      OfferedSkillRepository offeredSkills,
      CatalogItemRepository catalogItems,
      DeclareInterestsUseCase declareInterests) {
    this.offeredSkills = offeredSkills;
    this.catalogItems = catalogItems;
    this.declareInterests = declareInterests;
  }

  @Override
  public boolean isTutorEnabledFor(UUID tutorId, UUID catalogItemId) {
    String tenantId = TenantContext.require();
    return offeredSkills.existsByTenantIdAndTutorIdAndCatalogItemIdAndStatus(
        tenantId, tutorId, catalogItemId, OfferedSkillStatus.ENABLED);
  }

  @Override
  public List<UUID> enabledSkillsOf(UUID tutorId) {
    String tenantId = TenantContext.require();
    return offeredSkills.findCatalogItemIdByTenantIdAndTutorIdAndStatus(
        tenantId, tutorId, OfferedSkillStatus.ENABLED);
  }

  @Override
  public CatalogItemView requireItem(UUID catalogItemId) {
    String tenantId = TenantContext.require();
    CatalogItem item =
        catalogItems
            .findByIdAndTenantVisibility(catalogItemId, tenantId)
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "catalog item %s not found".formatted(catalogItemId)));
    return new CatalogItemView(item.getId(), item.getScope(), item.getName(), item.getCourseCode());
  }

  @Override
  public void declareLearningInterests(UUID studentId, List<UUID> catalogItemIds) {
    declareInterests.declareLearningInterests(studentId, catalogItemIds);
  }

  @Override
  public List<UUID> declareTeachingInterests(UUID studentId, List<UUID> catalogItemIds) {
    return declareInterests.declareTeachingInterests(studentId, catalogItemIds).stream()
        .map(OfferedSkill::getCatalogItemId)
        .toList();
  }
}
