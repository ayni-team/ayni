package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.ModerationQuery.ModerationDetail;
import pe.ayni.skills.application.ModerationQuery.ModerationItem;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/** US43, scenarios 1 and 5: what the moderator sees of the queue and of the history. */
class ModerationQueryTest {

  private static final String UPC = "UPC";
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID OTHER_COORDINATOR = UUID.randomUUID();
  private static final UUID DESIGN = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final IdentityApi identity = mock(IdentityApi.class);
  private final SkillProposalRepository proposals = mock(SkillProposalRepository.class);
  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final ModerationQuery query =
      new ModerationQuery(
          new CoordinatorGuard(identity),
          proposals,
          categories,
          catalogItems,
          new SimilarItemsQuery(catalogItems),
          identity);

  @BeforeEach
  void aCoordinatorAsks() {
    when(identity.requireUser(COORDINATOR)).thenReturn(user(COORDINATOR, UserRole.COORDINATOR, "Carla Ríos"));
    when(identity.requireUser(OTHER_COORDINATOR))
        .thenReturn(user(OTHER_COORDINATOR, UserRole.COORDINATOR, "Mario Paz"));
    when(identity.requireUser(STUDENT)).thenReturn(user(STUDENT, UserRole.STUDENT, "Ana Torres"));
    when(categories.findAllById(any(Iterable.class)))
        .thenReturn(List.of(new Category(DESIGN, "Design", (short) 0)));
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of());
  }

  private static UserView user(UUID id, UserRole role, String name) {
    return new UserView(id, UPC, role, "x@upc.edu.pe", "U202310949", name, "Software Engineering", "5", null);
  }

  private static CatalogItem tool(String name) {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, DESIGN, name, null, null, NOW);
  }

  private static SkillProposal waiting(String name) {
    return SkillProposal.propose(UUID.randomUUID(), UPC, STUDENT, DESIGN, name, "A tool", NOW);
  }

  private Page<ModerationItem> page(UUID asking, ProposalStatus status) {
    AtomicReference<Page<ModerationItem>> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.page(asking, status, 0, 20)));
    return result.get();
  }

  private ModerationDetail detail(UUID proposalId) {
    AtomicReference<ModerationDetail> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(query.detail(COORDINATOR, proposalId)));
    return result.get();
  }

  @Test
  @DisplayName("the queue shows each proposal with its name, category, proposer and since when it waits")
  void theQueueShowsEachProposal() {
    SkillProposal figma = waiting("Figma");
    when(proposals.findByTenantIdAndStatus(eq(UPC), eq(ProposalStatus.PROPOSED), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(figma), PageRequest.of(0, 20), 1));

    Page<ModerationItem> queue = page(COORDINATOR, ProposalStatus.PROPOSED);

    assertThat(queue.getContent()).singleElement().satisfies(item -> {
      assertThat(item.proposal()).isSameAs(figma);
      assertThat(item.categoryName()).isEqualTo("Design");
      assertThat(item.proposer().fullName()).isEqualTo("Ana Torres");
      assertThat(item.resolver()).isNull();
      assertThat(item.catalogItem()).isNull();
    });
  }

  @Test
  @DisplayName("the queue is oldest first and the history is newest resolved first")
  void theQueueIsOldestFirstAndTheHistoryNewestResolvedFirst() {
    when(proposals.findByTenantIdAndStatus(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

    page(COORDINATOR, ProposalStatus.PROPOSED);
    page(COORDINATOR, ProposalStatus.REJECTED);

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(proposals).findByTenantIdAndStatus(eq(UPC), eq(ProposalStatus.PROPOSED), pageable.capture());
    assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
    verify(proposals).findByTenantIdAndStatus(eq(UPC), eq(ProposalStatus.REJECTED), pageable.capture());
    assertThat(pageable.getValue().getSort().getOrderFor("resolvedAt").isDescending()).isTrue();
  }

  @Test
  @DisplayName("an empty page reads nothing else")
  void anEmptyPageReadsNothingElse() {
    when(proposals.findByTenantIdAndStatus(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

    assertThat(page(COORDINATOR, ProposalStatus.PROPOSED)).isEmpty();
    verifyNoInteractions(categories);
  }

  @Test
  @DisplayName("the history says who resolved each proposal, when, how, and the item it ended up in")
  void theHistorySaysWhoWhenAndHow() {
    CatalogItem nodeJs = tool("Node.js");
    SkillProposal merged = waiting("NodeJS");
    merged.mergeInto(OTHER_COORDINATOR, nodeJs.getId(), "It is Node.js", NOW.plusSeconds(60));
    SkillProposal rejected = waiting("Excel macros");
    rejected.reject(COORDINATOR, "Not a tool we teach", NOW.plusSeconds(30));
    when(proposals.findByTenantIdAndStatus(eq(UPC), eq(ProposalStatus.MERGED), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(merged), PageRequest.of(0, 20), 1));
    when(catalogItems.findAllById(List.of(nodeJs.getId()))).thenReturn(List.of(nodeJs));

    ModerationItem item = page(COORDINATOR, ProposalStatus.MERGED).getContent().get(0);

    assertThat(item.proposal().getStatus()).isEqualTo(ProposalStatus.MERGED);
    assertThat(item.resolver().fullName()).isEqualTo("Mario Paz");
    assertThat(item.proposal().getResolvedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(item.catalogItem()).isSameAs(nodeJs);
  }

  @Test
  @DisplayName("a rejection in the history has a reason and no item, and reads no item")
  void aRejectionHasAReasonAndNoItem() {
    SkillProposal rejected = waiting("Excel macros");
    rejected.reject(COORDINATOR, "Not a tool we teach", NOW);
    when(proposals.findByTenantIdAndStatus(eq(UPC), eq(ProposalStatus.REJECTED), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(rejected), PageRequest.of(0, 20), 1));

    ModerationItem item = page(COORDINATOR, ProposalStatus.REJECTED).getContent().get(0);

    assertThat(item.catalogItem()).isNull();
    assertThat(item.proposal().getDecisionReason()).isEqualTo("Not a tool we teach");
    assertThat(item.resolver().fullName()).isEqualTo("Carla Ríos");
    verify(catalogItems, never()).findAllById(any());
  }

  @Test
  @DisplayName("a student cannot read the queue and nothing is read")
  void aStudentCannotReadTheQueue() {
    assertThatThrownBy(() -> page(STUDENT, ProposalStatus.PROPOSED)).isInstanceOf(NotACoordinator.class);
    assertThatThrownBy(() -> TenantContext.runAs(UPC, () -> query.detail(STUDENT, UUID.randomUUID())))
        .isInstanceOf(NotACoordinator.class);

    verifyNoInteractions(proposals);
  }

  @Test
  @DisplayName("the detail of a waiting proposal lists the catalogue items it looks like")
  void theDetailOfAWaitingProposalListsTheSimilarItems() {
    CatalogItem nodeJs = tool("Node.js");
    SkillProposal proposal = waiting("NodeJS Advanced");
    when(proposals.findByTenantIdAndId(UPC, proposal.getId())).thenReturn(Optional.of(proposal));
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of(nodeJs, tool("Figma")));

    ModerationDetail detail = detail(proposal.getId());

    assertThat(detail.item().proposal()).isSameAs(proposal);
    assertThat(detail.similar()).containsExactly(nodeJs);
  }

  @Test
  @DisplayName("the detail of a resolved proposal lists no similar items: nothing is left to decide")
  void theDetailOfAResolvedProposalListsNoSimilarItems() {
    SkillProposal proposal = waiting("NodeJS Advanced");
    proposal.reject(COORDINATOR, "No", NOW);
    when(proposals.findByTenantIdAndId(UPC, proposal.getId())).thenReturn(Optional.of(proposal));
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of(tool("Node.js")));

    assertThat(detail(proposal.getId()).similar()).isEmpty();
  }

  @Test
  @DisplayName("a proposal that is not in this university is not found")
  void aProposalThatIsNotInThisUniversityIsNotFound() {
    UUID missing = UUID.randomUUID();
    when(proposals.findByTenantIdAndId(UPC, missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> detail(missing)).isInstanceOf(NoSuchElementException.class);
  }
}
