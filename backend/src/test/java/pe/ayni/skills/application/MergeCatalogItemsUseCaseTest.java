package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.events.SkillWithdrawn;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.MergeCatalogItemsUseCase.Merge;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.OfferedSkillStatus;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;
import pe.ayni.skills.infrastructure.TaughtSessionRepository;

/** US44, scenario 1: what joining two items moves, and what stops it. */
class MergeCatalogItemsUseCaseTest {

  private static final String UPC = "UPC";
  private static final String UTEC = "UTEC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final LearningInterestRepository interests = mock(LearningInterestRepository.class);
  private final SkillProposalRepository proposals = mock(SkillProposalRepository.class);
  private final TaughtSessionRepository taughtSessions = mock(TaughtSessionRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final MergeCatalogItemsUseCase useCase =
      new MergeCatalogItemsUseCase(
          new CoordinatorGuard(identity),
          catalogItems,
          offeredSkills,
          interests,
          proposals,
          taughtSessions,
          events,
          Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));

  private CatalogItem duplicate;
  private CatalogItem kept;

  @BeforeEach
  void twoToolsThatAreTheSame() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    duplicate = tool("Node JS");
    kept = tool("Node.js");
    found(duplicate);
    found(kept);
    when(offeredSkills.lockByCatalogItemId(duplicate.getId())).thenReturn(List.of());
    when(offeredSkills.lockByCatalogItemId(kept.getId())).thenReturn(List.of());
  }

  private static CatalogItem tool(String name) {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, UUID.randomUUID(), name, null, null, NOW);
  }

  private void found(CatalogItem item) {
    when(catalogItems.lockByIdAndTenantVisibility(item.getId(), UPC)).thenReturn(Optional.of(item));
  }

  private OfferedSkill enabled(String tenant, UUID tutor, CatalogItem item) {
    OfferedSkill skill = OfferedSkill.requestValidation(UUID.randomUUID(), tenant, tutor, item.getId(), NOW);
    skill.approveByReviewedEvidence(NOW);
    return skill;
  }

  private Merge merge(UUID asking, UUID removedId, UUID keptId) {
    AtomicReference<Merge> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(useCase.execute(asking, removedId, keptId)));
    return result.get();
  }

  private Merge merge() {
    return merge(COORDINATOR, duplicate.getId(), kept.getId());
  }

  @Test
  @DisplayName("an enabled offer moves to the item that stays and the search is told the old one ended and the new one began")
  void anEnabledOfferMovesAndTheSearchIsTold() {
    UUID tutor = UUID.randomUUID();
    OfferedSkill offer = enabled(UTEC, tutor, duplicate);
    when(offeredSkills.lockByCatalogItemId(duplicate.getId())).thenReturn(List.of(offer));

    Merge merge = merge();

    assertThat(offer.getCatalogItemId()).isEqualTo(kept.getId());
    assertThat(offer.isEnabled()).isTrue();
    assertThat(offer.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(merge.offersMoved()).isEqualTo(1);
    assertThat(merge.offersWithdrawn()).isZero();
    ArgumentCaptor<Object> announced = ArgumentCaptor.forClass(Object.class);
    verify(events, times(2)).publishEvent(announced.capture());
    assertThat(announced.getAllValues().get(0))
        .isEqualTo(new SkillWithdrawn(UTEC, tutor, duplicate.getId(), NOW.plusSeconds(60)));
    assertThat(announced.getAllValues().get(1))
        .isEqualTo(new SkillEnabled(UTEC, tutor, kept.getId(), NOW.plusSeconds(60)));
  }

  @Test
  @DisplayName("an offer that is not enabled moves with its status, and nothing is announced")
  void anOfferThatIsNotEnabledMovesWithItsStatus() {
    OfferedSkill pending = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, UUID.randomUUID(), duplicate.getId(), NOW);
    OfferedSkill rejected = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, UUID.randomUUID(), duplicate.getId(), NOW);
    rejected.rejectEvidence(NOW);
    OfferedSkill withdrawn = enabled(UPC, UUID.randomUUID(), duplicate);
    withdrawn.withdraw(NOW);
    when(offeredSkills.lockByCatalogItemId(duplicate.getId())).thenReturn(List.of(pending, rejected, withdrawn));

    Merge merge = merge();

    assertThat(List.of(pending, rejected, withdrawn)).allSatisfy(skill -> assertThat(skill.getCatalogItemId()).isEqualTo(kept.getId()));
    assertThat(pending.getStatus()).isEqualTo(OfferedSkillStatus.PENDING);
    assertThat(rejected.getStatus()).isEqualTo(OfferedSkillStatus.REJECTED);
    assertThat(withdrawn.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    assertThat(merge.offersMoved()).isEqualTo(3);
    verifyNoInteractions(events);
  }

  @Test
  @DisplayName("a tutor who held both keeps the offer on the item that stays and has the other withdrawn")
  void aTutorWhoHeldBothKeepsTheOneThatStays() {
    UUID tutor = UUID.randomUUID();
    OfferedSkill onDuplicate = enabled(UPC, tutor, duplicate);
    OfferedSkill onKept = enabled(UPC, tutor, kept);
    when(offeredSkills.lockByCatalogItemId(duplicate.getId())).thenReturn(List.of(onDuplicate));
    when(offeredSkills.lockByCatalogItemId(kept.getId())).thenReturn(List.of(onKept));

    Merge merge = merge();

    assertThat(onKept.isEnabled()).isTrue();
    assertThat(onKept.getCatalogItemId()).isEqualTo(kept.getId());
    assertThat(onDuplicate.getStatus()).isEqualTo(OfferedSkillStatus.WITHDRAWN);
    assertThat(onDuplicate.getCatalogItemId()).isEqualTo(duplicate.getId());
    assertThat(merge.offersMoved()).isZero();
    assertThat(merge.offersWithdrawn()).isEqualTo(1);
    ArgumentCaptor<SkillWithdrawn> announced = ArgumentCaptor.forClass(SkillWithdrawn.class);
    verify(events).publishEvent(announced.capture());
    assertThat(announced.getValue().catalogItemId()).isEqualTo(duplicate.getId());
    assertThat(announced.getValue().tutorId()).isEqualTo(tutor);
  }

  @Test
  @DisplayName("the same tutor id in another university is another person: that offer still moves")
  void theSameTutorIdInAnotherUniversityStillMoves() {
    UUID tutor = UUID.randomUUID();
    OfferedSkill onDuplicate = enabled(UTEC, tutor, duplicate);
    OfferedSkill onKept = enabled(UPC, tutor, kept);
    when(offeredSkills.lockByCatalogItemId(duplicate.getId())).thenReturn(List.of(onDuplicate));
    when(offeredSkills.lockByCatalogItemId(kept.getId())).thenReturn(List.of(onKept));

    Merge merge = merge();

    assertThat(onDuplicate.getCatalogItemId()).isEqualTo(kept.getId());
    assertThat(merge.offersMoved()).isEqualTo(1);
    assertThat(merge.offersWithdrawn()).isZero();
  }

  @Test
  @DisplayName("a duplicate that was not enabled for a tutor who held both is left as it was")
  void aDuplicateThatWasNotEnabledIsLeftAsItWas() {
    UUID tutor = UUID.randomUUID();
    OfferedSkill rejected = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, tutor, duplicate.getId(), NOW);
    rejected.rejectEvidence(NOW);
    when(offeredSkills.lockByCatalogItemId(duplicate.getId())).thenReturn(List.of(rejected));
    when(offeredSkills.lockByCatalogItemId(kept.getId())).thenReturn(List.of(enabled(UPC, tutor, kept)));

    Merge merge = merge();

    assertThat(rejected.getStatus()).isEqualTo(OfferedSkillStatus.REJECTED);
    assertThat(rejected.getCatalogItemId()).isEqualTo(duplicate.getId());
    assertThat(merge.offersWithdrawn()).isZero();
    verifyNoInteractions(events);
  }

  @Test
  @DisplayName("the duplicate is retired, the one that stays is not, and the rest follows to it")
  void theDuplicateIsRetiredAndTheRestFollows() {
    when(interests.deleteDuplicatesOf(duplicate.getId(), kept.getId())).thenReturn(2);
    when(interests.moveInterests(duplicate.getId(), kept.getId())).thenReturn(5);
    when(proposals.repoint(duplicate.getId(), kept.getId())).thenReturn(1);
    when(taughtSessions.carryOver(duplicate.getId(), kept.getId())).thenReturn(12);

    Merge merge = merge();

    assertThat(duplicate.isActive()).isFalse();
    assertThat(kept.isActive()).isTrue();
    assertThat(merge.removed()).isSameAs(duplicate);
    assertThat(merge.kept()).isSameAs(kept);
    assertThat(merge.interestsDropped()).isEqualTo(2);
    assertThat(merge.interestsMoved()).isEqualTo(5);
    assertThat(merge.proposalsRepointed()).isEqualTo(1);
    assertThat(merge.sessionsCarriedOver()).isEqualTo(12);
  }

  @Test
  @DisplayName("the duplicate is retired and the items are written before the bulk updates")
  void theItemsAreWrittenBeforeTheBulkUpdates() {
    merge();

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(catalogItems, interests);
    order.verify(catalogItems).flush();
    order.verify(interests).deleteDuplicatesOf(any(), any());
    order.verify(interests).moveInterests(any(), any());
  }

  @Test
  @DisplayName("the same item twice is refused before anything is read")
  void theSameItemTwiceIsRefused() {
    assertThatThrownBy(() -> merge(COORDINATOR, duplicate.getId(), duplicate.getId()))
        .isInstanceOf(SkillsRuleViolation.class);

    verifyNoInteractions(catalogItems, offeredSkills, interests, proposals, taughtSessions, events);
  }

  @Test
  @DisplayName("a course and a tool cannot be joined, and nothing moves")
  void aCourseAndAToolCannotBeJoined() {
    CatalogItem course =
        new CatalogItem(UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Node course", null, "C1", NOW);
    found(course);

    assertThatThrownBy(() -> merge(COORDINATOR, course.getId(), kept.getId())).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> merge(COORDINATOR, kept.getId(), course.getId())).isInstanceOf(SkillsRuleViolation.class);

    verifyNoInteractions(interests, proposals, taughtSessions, events);
    assertThat(kept.isActive()).isTrue();
    assertThat(course.isActive()).isTrue();
  }

  @Test
  @DisplayName("two courses of the university can be joined")
  void twoCoursesOfTheUniversityCanBeJoined() {
    CatalogItem oldCourse =
        new CatalogItem(UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Old", null, "C1", NOW);
    CatalogItem newCourse =
        new CatalogItem(UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "New", null, "C2", NOW);
    found(oldCourse);
    found(newCourse);
    when(offeredSkills.lockByCatalogItemId(oldCourse.getId())).thenReturn(List.of());
    when(offeredSkills.lockByCatalogItemId(newCourse.getId())).thenReturn(List.of());

    merge(COORDINATOR, oldCourse.getId(), newCourse.getId());

    assertThat(oldCourse.isActive()).isFalse();
    assertThat(newCourse.isActive()).isTrue();
  }

  @Test
  @DisplayName("a duplicate already retired is a conflict, and an item that would stay retired is refused")
  void aRetiredItemIsRefused() {
    duplicate.retire();
    assertThatThrownBy(() -> merge()).isInstanceOf(SkillsStateConflict.class);

    CatalogItem active = tool("Active");
    CatalogItem retiredOne = tool("Retired");
    retiredOne.retire();
    found(active);
    found(retiredOne);
    assertThatThrownBy(() -> merge(COORDINATOR, active.getId(), retiredOne.getId()))
        .isInstanceOf(SkillsRuleViolation.class)
        .isNotInstanceOf(SkillsStateConflict.class);

    verifyNoInteractions(interests, proposals, taughtSessions, events);
  }

  @Test
  @DisplayName("an item that does not exist, or is a course of another university, is not found")
  void anItemThatIsNotVisibleIsNotFound() {
    UUID unseen = UUID.randomUUID();
    when(catalogItems.lockByIdAndTenantVisibility(unseen, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> merge(COORDINATOR, duplicate.getId(), unseen)).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> merge(COORDINATOR, unseen, kept.getId())).isInstanceOf(NoSuchElementException.class);

    assertThat(duplicate.isActive()).isTrue();
  }

  @Test
  @DisplayName("a student cannot join items, and nothing is read")
  void aStudentCannotJoinItems() {
    assertThatThrownBy(() -> merge(STUDENT, duplicate.getId(), kept.getId())).isInstanceOf(NotACoordinator.class);

    verifyNoInteractions(catalogItems, offeredSkills, interests, proposals, taughtSessions, events);
  }

  @Test
  @DisplayName("the two items are locked in the same order whichever way they are joined")
  void theItemsAreLockedInTheSameOrder() {
    UUID low = UUID.fromString("00000000-0000-4000-8000-000000000001");
    UUID high = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");

    CatalogItem lowOne = withId(low);
    CatalogItem highOne = withId(high);
    found(lowOne);
    found(highOne);
    when(offeredSkills.lockByCatalogItemId(low)).thenReturn(List.of());
    when(offeredSkills.lockByCatalogItemId(high)).thenReturn(List.of());
    merge(COORDINATOR, low, high);
    ArgumentCaptor<UUID> forward = ArgumentCaptor.forClass(UUID.class);
    verify(catalogItems, times(2)).lockByIdAndTenantVisibility(forward.capture(), any());
    List<UUID> forwardOrder = List.copyOf(forward.getAllValues());

    org.mockito.Mockito.clearInvocations(catalogItems);
    CatalogItem lowAgain = withId(low);
    CatalogItem highAgain = withId(high);
    found(lowAgain);
    found(highAgain);
    merge(COORDINATOR, high, low);
    ArgumentCaptor<UUID> backward = ArgumentCaptor.forClass(UUID.class);
    verify(catalogItems, times(2)).lockByIdAndTenantVisibility(backward.capture(), any());

    // Whatever the order is, it is the same one both ways, and that is what prevents a deadlock.
    assertThat(forwardOrder).containsExactlyInAnyOrder(low, high);
    assertThat(backward.getAllValues()).isEqualTo(forwardOrder);
  }

  private static CatalogItem withId(UUID id) {
    return new CatalogItem(id, CatalogScope.GLOBAL, null, UUID.randomUUID(), "Item " + id, null, null, NOW);
  }
}
