package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.RetireCatalogItemUseCase.Retirement;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.GlobalCatalogItemNotModerable;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/** US44, scenarios 2 and 3: what retiring an item does, and what stops it. */
class RetireCatalogItemUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final RetireCatalogItemUseCase useCase =
      new RetireCatalogItemUseCase(
          new CoordinatorGuard(identity),
          catalogItems,
          offeredSkills,
          events,
          Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));

  private CatalogItem figma;

  @BeforeEach
  void aToolAndACoordinator() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    figma =
        new CatalogItem(
            UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Calculus I", null, "MA101", NOW);
    when(catalogItems.lockByIdAndTenantVisibility(figma.getId(), UPC)).thenReturn(Optional.of(figma));
    when(offeredSkills.lockByTenantIdAndCatalogItemIdAndStatus(UPC, figma.getId(), OfferedSkillStatus.ENABLED))
        .thenReturn(List.of());
  }

  private OfferedSkill offeredBy(String tenant) {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), tenant, UUID.randomUUID(), figma.getId(), NOW);
    skill.approveByReviewedEvidence(NOW);
    return skill;
  }

  private Retirement retire(UUID asking, long confirmed) {
    AtomicReference<Retirement> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(useCase.execute(asking, figma.getId(), confirmed)));
    return result.get();
  }

  @Test
  @DisplayName("retiring withdraws every offer of the university and announces it for each tutor")
  void retiringWithdrawsEveryOfferAndAnnouncesIt() {
    OfferedSkill first = offeredBy(UPC);
    OfferedSkill second = offeredBy(UPC);
    when(offeredSkills.lockByTenantIdAndCatalogItemIdAndStatus(UPC, figma.getId(), OfferedSkillStatus.ENABLED))
        .thenReturn(List.of(first, second));

    Retirement retirement = retire(COORDINATOR, 2);

    assertThat(figma.isActive()).isFalse();
    assertThat(first.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    assertThat(second.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    assertThat(retirement.tutorsAffected()).isEqualTo(2);
    assertThat(retirement.retiredAt()).isEqualTo(NOW.plusSeconds(60));
    ArgumentCaptor<SkillWithdrawn> announced = ArgumentCaptor.forClass(SkillWithdrawn.class);
    verify(events, org.mockito.Mockito.times(2)).publishEvent(announced.capture());
    assertThat(announced.getAllValues())
        .extracting(SkillWithdrawn::tenantId, SkillWithdrawn::tutorId, SkillWithdrawn::catalogItemId)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(UPC, first.getTutorId(), figma.getId()),
            org.assertj.core.groups.Tuple.tuple(UPC, second.getTutorId(), figma.getId()));
  }

  @Test
  @DisplayName("an item nobody offers is retired with zero tutors affected and nothing announced")
  void anItemNobodyOffersIsRetiredQuietly() {
    Retirement retirement = retire(COORDINATOR, 0);

    assertThat(figma.isActive()).isFalse();
    assertThat(retirement.tutorsAffected()).isZero();
    verifyNoInteractions(events);
  }

  @Test
  @DisplayName("if the number of tutors is not the one confirmed, nothing is retired and the real number is said")
  void ifTheNumberChangedNothingIsRetired() {
    OfferedSkill offer = offeredBy(UPC);
    when(offeredSkills.lockByTenantIdAndCatalogItemIdAndStatus(UPC, figma.getId(), OfferedSkillStatus.ENABLED))
        .thenReturn(List.of(offer));

    assertThatThrownBy(() -> retire(COORDINATOR, 3))
        .isInstanceOf(SkillsStateConflict.class)
        .hasMessageContaining("1 tutors offer this skill now, not 3");

    assertThat(figma.isActive()).isTrue();
    assertThat(offer.isEnabled()).isTrue();
    verifyNoInteractions(events);
  }

  @Test
  @DisplayName("an item already retired is a conflict and nothing is announced again")
  void anItemAlreadyRetiredIsAConflict() {
    figma.retire();

    assertThatThrownBy(() -> retire(COORDINATOR, 0)).isInstanceOf(SkillsStateConflict.class);

    verify(offeredSkills, never()).lockByTenantIdAndCatalogItemIdAndStatus(any(), any(), any());
    verifyNoInteractions(events);
  }

  @Test
  @DisplayName("a global tool is not the coordinator's to retire: nothing is withdrawn or announced")
  void aGlobalToolIsNotTheCoordinatorsToRetire() {
    CatalogItem global =
        new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, UUID.randomUUID(), "Figma", null, null, NOW);
    when(catalogItems.lockByIdAndTenantVisibility(global.getId(), UPC)).thenReturn(Optional.of(global));

    assertThatThrownBy(() -> TenantContext.runAs(UPC, () -> useCase.execute(COORDINATOR, global.getId(), 0)))
        .isInstanceOf(GlobalCatalogItemNotModerable.class);

    assertThat(global.isActive()).isTrue();
    verifyNoInteractions(offeredSkills, events);
  }

  @Test
  @DisplayName("a negative confirmation is refused before anything is read")
  void aNegativeConfirmationIsRefused() {
    assertThatThrownBy(() -> retire(COORDINATOR, -1)).isInstanceOf(SkillsRuleViolation.class);

    verifyNoInteractions(catalogItems, offeredSkills, events);
  }

  @Test
  @DisplayName("an item that does not exist, or is a course of another university, is not found")
  void anItemThatIsNotVisibleIsNotFound() {
    UUID unseen = UUID.randomUUID();
    when(catalogItems.lockByIdAndTenantVisibility(unseen, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> TenantContext.runAs(UPC, () -> useCase.execute(COORDINATOR, unseen, 0)))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("a student cannot retire an item, and nothing is read")
  void aStudentCannotRetireAnItem() {
    assertThatThrownBy(() -> retire(STUDENT, 0)).isInstanceOf(NotACoordinator.class);

    verifyNoInteractions(catalogItems, offeredSkills, events);
    assertThat(figma.isActive()).isTrue();
  }
}
