package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.ResolveProposalUseCase.Decision;
import pe.ayni.skills.application.ResolveProposalUseCase.Resolution;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/** US43, scenarios 2, 3 and 4: what each decision does, and what stops it. */
class ResolveProposalUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID MODERATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID CATEGORY = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final SkillProposalRepository proposals = mock(SkillProposalRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final ResolveProposalUseCase useCase =
      new ResolveProposalUseCase(
          new CoordinatorGuard(identity),
          proposals,
          catalogItems,
          new SimilarItemsQuery(catalogItems),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private SkillProposal proposal;

  @BeforeEach
  void aWaitingProposalAndACoordinator() {
    when(identity.requireUser(MODERATOR))
        .thenReturn(new UserView(MODERATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    proposal = SkillProposal.propose(UUID.randomUUID(), UPC, STUDENT, CATEGORY, "Figma", "Design tool", NOW.minusSeconds(60));
    when(proposals.lockByTenantIdAndId(UPC, proposal.getId())).thenReturn(Optional.of(proposal));
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of());
    when(catalogItems.saveAndFlush(any(CatalogItem.class))).thenAnswer(call -> call.getArgument(0));
  }

  private static CatalogItem existing(String name) {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, CATEGORY, name, null, null, NOW);
  }

  private Resolution decide(UUID asking, UUID proposalId, Decision decision, UUID item, String reason) {
    AtomicReference<Resolution> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(useCase.execute(asking, proposalId, decision, item, reason)));
    return result.get();
  }

  private Resolution decide(Decision decision, UUID item, String reason) {
    return decide(MODERATOR, proposal.getId(), decision, item, reason);
  }

  @Test
  @DisplayName("approving adds the tool to the catalogue as a global item and records who decided")
  void approvingAddsTheToolAsAGlobalItem() {
    Resolution resolution = decide(Decision.APPROVE, null, null);

    ArgumentCaptor<CatalogItem> saved = ArgumentCaptor.forClass(CatalogItem.class);
    verify(catalogItems).saveAndFlush(saved.capture());
    CatalogItem created = saved.getValue();
    assertThat(created.getScope()).isEqualTo(CatalogScope.GLOBAL);
    assertThat(created.getTenantId()).isNull();
    assertThat(created.getName()).isEqualTo("Figma");
    assertThat(created.getDescription()).isEqualTo("Design tool");
    assertThat(created.getCategoryId()).isEqualTo(CATEGORY);
    assertThat(created.getCourseCode()).isNull();
    assertThat(created.isActive()).isTrue();
    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.APPROVED);
    assertThat(proposal.getCatalogItemId()).isEqualTo(created.getId());
    assertThat(proposal.getResolvedBy()).isEqualTo(MODERATOR);
    assertThat(proposal.getResolvedAt()).isEqualTo(NOW);
    assertThat(resolution.item()).isSameAs(created);
  }

  @Test
  @DisplayName("approving waits its turn for the global catalogue before it looks for the name")
  void approvingWaitsItsTurnBeforeLookingForTheName() {
    decide(Decision.APPROVE, null, null);

    InOrder order = inOrder(catalogItems);
    order.verify(catalogItems).lockGlobalCatalogue();
    order.verify(catalogItems).findVisible(UPC, CatalogItemStatus.ACTIVE);
    order.verify(catalogItems).saveAndFlush(any(CatalogItem.class));
  }

  @Test
  @DisplayName("approving a name the catalogue already has is a conflict that points at joining it")
  void approvingANameTheCatalogueAlreadyHasIsAConflict() {
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of(existing("FIGMA")));

    assertThatThrownBy(() -> decide(Decision.APPROVE, null, null))
        .isInstanceOf(SkillsStateConflict.class)
        .hasMessageContaining("join the proposal");

    verify(catalogItems, never()).saveAndFlush(any());
    assertThat(proposal.isWaiting()).isTrue();
  }

  @Test
  @DisplayName("a similar name does not stop an approval: only the same name does")
  void aSimilarNameDoesNotStopAnApproval() {
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of(existing("Figma Advanced")));

    decide(Decision.APPROVE, null, null);

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.APPROVED);
  }

  @Test
  @DisplayName("joining creates nothing and records the item the proposal was joined to")
  void joiningCreatesNothingAndRecordsTheItem() {
    CatalogItem nodeJs = existing("Node.js");
    when(catalogItems.findByIdAndTenantVisibility(nodeJs.getId(), UPC)).thenReturn(Optional.of(nodeJs));

    Resolution resolution = decide(Decision.MERGE, nodeJs.getId(), "It is Node.js");

    verify(catalogItems, never()).saveAndFlush(any());
    verify(catalogItems, never()).lockGlobalCatalogue();
    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.MERGED);
    assertThat(proposal.getCatalogItemId()).isEqualTo(nodeJs.getId());
    assertThat(proposal.getResolvedBy()).isEqualTo(MODERATOR);
    assertThat(proposal.getDecisionReason()).isEqualTo("It is Node.js");
    assertThat(resolution.item()).isSameAs(nodeJs);
  }

  @Test
  @DisplayName("joining needs the item, and an item that is not visible, or is retired, is refused")
  void joiningNeedsAVisibleActiveItem() {
    UUID unseen = UUID.randomUUID();
    when(catalogItems.findByIdAndTenantVisibility(unseen, UPC)).thenReturn(Optional.empty());
    CatalogItem retired = existing("Old tool");
    retired.retire();
    when(catalogItems.findByIdAndTenantVisibility(retired.getId(), UPC)).thenReturn(Optional.of(retired));

    assertThatThrownBy(() -> decide(Decision.MERGE, null, null)).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> decide(Decision.MERGE, unseen, null)).isInstanceOf(NoSuchElementException.class);
    assertThatThrownBy(() -> decide(Decision.MERGE, retired.getId(), null)).isInstanceOf(SkillsRuleViolation.class);

    assertThat(proposal.isWaiting()).isTrue();
  }

  @Test
  @DisplayName("an item goes only with a decision to join, and is refused before anything is read")
  void anItemGoesOnlyWithAMerge() {
    assertThatThrownBy(() -> decide(Decision.APPROVE, UUID.randomUUID(), null)).isInstanceOf(SkillsRuleViolation.class);
    assertThatThrownBy(() -> decide(Decision.REJECT, UUID.randomUUID(), "No")).isInstanceOf(SkillsRuleViolation.class);

    verify(proposals, never()).lockByTenantIdAndId(any(), any());
  }

  @Test
  @DisplayName("rejecting records who, when and the reason, and creates no item")
  void rejectingRecordsWhoWhenAndTheReason() {
    Resolution resolution = decide(Decision.REJECT, null, "Not a tool we teach");

    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
    assertThat(proposal.getDecisionReason()).isEqualTo("Not a tool we teach");
    assertThat(proposal.getResolvedBy()).isEqualTo(MODERATOR);
    assertThat(proposal.getCatalogItemId()).isNull();
    assertThat(resolution.item()).isNull();
    verify(catalogItems, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("a rejection without a reason is refused and the proposal keeps waiting")
  void aRejectionWithoutAReasonIsRefused() {
    assertThatThrownBy(() -> decide(Decision.REJECT, null, "  ")).isInstanceOf(SkillsRuleViolation.class);

    assertThat(proposal.isWaiting()).isTrue();
  }

  @Test
  @DisplayName("a proposal already resolved is a conflict, and an approval creates no item")
  void aProposalAlreadyResolvedIsAConflict() {
    proposal.reject(MODERATOR, "No", NOW.minusSeconds(10));

    assertThatThrownBy(() -> decide(Decision.APPROVE, null, null)).isInstanceOf(SkillsStateConflict.class);
    assertThatThrownBy(() -> decide(Decision.REJECT, null, "Again")).isInstanceOf(SkillsStateConflict.class);

    verify(catalogItems, never()).saveAndFlush(any());
    verify(catalogItems, never()).lockGlobalCatalogue();
    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);
  }

  @Test
  @DisplayName("a proposal that is not in this university is not found")
  void aProposalThatIsNotInThisUniversityIsNotFound() {
    UUID missing = UUID.randomUUID();
    when(proposals.lockByTenantIdAndId(UPC, missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> decide(MODERATOR, missing, Decision.REJECT, null, "No"))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  @DisplayName("a student cannot decide, and nothing is read")
  void aStudentCannotDecide() {
    assertThatThrownBy(() -> decide(STUDENT, proposal.getId(), Decision.APPROVE, null, null))
        .isInstanceOf(NotACoordinator.class);

    verifyNoInteractions(proposals);
    assertThat(proposal.isWaiting()).isTrue();
  }
}
