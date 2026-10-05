package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.CatalogUsageQuery.CatalogUsage;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/** US44, scenario 4: the use a moderator reviews an item by. */
class CatalogUsageQueryTest {

  private static final String UPC = "UPC";
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final IdentityApi identity = mock(IdentityApi.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final TaughtSessionRepository taughtSessions = mock(TaughtSessionRepository.class);
  private final CatalogUsageQuery query =
      new CatalogUsageQuery(new CoordinatorGuard(identity), catalogItems, offeredSkills, taughtSessions);

  @BeforeEach
  void aCoordinatorAndAStudent() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
  }

  private static CatalogItem tool() {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, UUID.randomUUID(), "Figma", null, null, NOW);
  }

  private CatalogUsage usageOf(UUID asking, UUID itemId) {
    AtomicReference<CatalogUsage> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.of(asking, itemId)));
    return result.get();
  }

  @Test
  @DisplayName("an item comes with the tutors who offer it and the sessions taught on it")
  void anItemComesWithItsUse() {
    CatalogItem figma = tool();
    when(catalogItems.findByIdAndTenantVisibility(figma.getId(), UPC)).thenReturn(Optional.of(figma));
    when(offeredSkills.countByCatalogItemIdAndStatus(figma.getId(), OfferedSkillStatus.ENABLED)).thenReturn(12L);
    when(taughtSessions.countByCatalogItemId(figma.getId())).thenReturn(48L);

    CatalogUsage usage = usageOf(COORDINATOR, figma.getId());

    assertThat(usage.item()).isSameAs(figma);
    assertThat(usage.tutorsOffering()).isEqualTo(12);
    assertThat(usage.sessionsTaught()).isEqualTo(48);
  }

  @Test
  @DisplayName("an item nobody offers and nobody taught has zeros")
  void anItemNobodyUsesHasZeros() {
    CatalogItem figma = tool();
    when(catalogItems.findByIdAndTenantVisibility(figma.getId(), UPC)).thenReturn(Optional.of(figma));

    CatalogUsage usage = usageOf(COORDINATOR, figma.getId());

    assertThat(usage.tutorsOffering()).isZero();
    assertThat(usage.sessionsTaught()).isZero();
  }

  @Test
  @DisplayName("a retired item is reviewed too")
  void aRetiredItemIsReviewedToo() {
    CatalogItem figma = tool();
    figma.retire();
    when(catalogItems.findByIdAndTenantVisibility(figma.getId(), UPC)).thenReturn(Optional.of(figma));

    assertThat(usageOf(COORDINATOR, figma.getId()).item().isActive()).isFalse();
  }

  @Test
  @DisplayName("an item that does not exist, or is a course of another university, is not found")
  void anItemThatIsNotVisibleIsNotFound() {
    UUID unseen = UUID.randomUUID();
    when(catalogItems.findByIdAndTenantVisibility(unseen, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> usageOf(COORDINATOR, unseen)).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("a student cannot read the use of an item, and nothing is read")
  void aStudentCannotReadTheUse() {
    assertThatThrownBy(() -> usageOf(STUDENT, UUID.randomUUID())).isInstanceOf(NotACoordinator.class);

    verifyNoInteractions(catalogItems, offeredSkills, taughtSessions);
  }
}
