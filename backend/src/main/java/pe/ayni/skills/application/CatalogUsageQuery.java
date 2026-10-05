package pe.ayni.skills.application;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/**
 * US44, scenario 4 and the preview of scenario 3: how much a catalogue item is used, which is what a
 * moderator reviews the catalogue by.
 *
 * <p>The numbers count every university. A global tool is offered and taught in all of them, and
 * retiring or joining it affects all of them, so the moderator must see all of them. They are
 * counts and never describe a person.
 */
@Service
public class CatalogUsageQuery {

  private final CoordinatorGuard moderators;
  private final CatalogItemRepository catalogItems;
  private final OfferedSkillRepository offeredSkills;
  private final TaughtSessionRepository taughtSessions;

  CatalogUsageQuery(
      CoordinatorGuard moderators,
      CatalogItemRepository catalogItems,
      OfferedSkillRepository offeredSkills,
      TaughtSessionRepository taughtSessions) {
    this.moderators = moderators;
    this.catalogItems = catalogItems;
    this.offeredSkills = offeredSkills;
    this.taughtSessions = taughtSessions;
  }

  /**
   * An item and its use.
   *
   * @param tutorsOffering tutors whose offer of the item is enabled right now: the ones a
   *     retirement would affect
   * @param sessionsTaught verified sessions taught on the item since skills started counting them
   */
  public record CatalogUsage(CatalogItem item, long tutorsOffering, long sessionsTaught) {}

  /**
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person asking is not a coordinator
   * @throws NoSuchElementException when the item does not exist, or is a course of another
   *     university
   */
  @Transactional(readOnly = true)
  public CatalogUsage of(UUID moderatorId, UUID catalogItemId) {
    Objects.requireNonNull(catalogItemId, "catalogItemId must not be null");
    moderators.require(moderatorId);
    String tenantId = TenantContext.require();

    // Any status: a moderator also reviews what was already retired.
    CatalogItem item =
        catalogItems
            .findByIdAndTenantVisibility(catalogItemId, tenantId)
            .orElseThrow(() -> new NoSuchElementException("catalogue item %s not found".formatted(catalogItemId)));
    return new CatalogUsage(item, tutorsOffering(item.getId()), taughtSessions.countByCatalogItemId(item.getId()));
  }

  /** Also what retiring or joining the item will touch, so those use cases share the number. */
  long tutorsOffering(UUID catalogItemId) {
    return offeredSkills.countByCatalogItemIdAndStatus(catalogItemId, OfferedSkillStatus.ENABLED);
  }
}
