package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.ProposalStatus;
import pe.ayni.skills.domain.model.SimilarSkillsFound;
import pe.ayni.skills.domain.model.SkillProposal;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;
import pe.ayni.skills.infrastructure.SkillProposalRepository;

/** US42, scenarios 1 and 2: when a proposal is recorded and when it is stopped. */
class ProposeSkillUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID CATEGORY = UUID.randomUUID();

  private final SkillProposalRepository proposals = mock(SkillProposalRepository.class);
  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final ProposeSkillUseCase useCase =
      new ProposeSkillUseCase(
          proposals,
          categories,
          new SimilarItemsQuery(catalogItems),
          Clock.fixed(NOW, ZoneOffset.UTC));

  ProposeSkillUseCaseTest() {
    when(categories.findById(CATEGORY))
        .thenReturn(Optional.of(new Category(CATEGORY, "Design", (short) 0)));
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of());
    when(proposals.saveAndFlush(any(SkillProposal.class))).thenAnswer(call -> call.getArgument(0));
  }

  private static CatalogItem inCatalogue(String name) {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.GLOBAL, null, CATEGORY, name, null, null, NOW);
  }

  private ProposalView propose(String name, boolean confirmDistinct) {
    AtomicReference<ProposalView> result = new AtomicReference<>();
    run(() -> result.set(useCase.execute(STUDENT, CATEGORY, name, "A short note", confirmDistinct)));
    return result.get();
  }

  private static void run(Runnable work) {
    TenantContext.runAs(UPC, work);
  }

  @Test
  @DisplayName("a skill the catalogue does not have is recorded as a proposal of the student")
  void aSkillTheCatalogueDoesNotHaveIsRecorded() {
    ProposalView view = propose("Figma", false);

    ArgumentCaptor<SkillProposal> saved = ArgumentCaptor.forClass(SkillProposal.class);
    verify(proposals).saveAndFlush(saved.capture());
    SkillProposal proposal = saved.getValue();
    assertThat(proposal.getTenantId()).isEqualTo(UPC);
    assertThat(proposal.getProposedBy()).isEqualTo(STUDENT);
    assertThat(proposal.getCategoryId()).isEqualTo(CATEGORY);
    assertThat(proposal.getName()).isEqualTo("Figma");
    assertThat(proposal.getDescription()).isEqualTo("A short note");
    assertThat(proposal.getStatus()).isEqualTo(ProposalStatus.PROPOSED);
    assertThat(proposal.getCreatedAt()).isEqualTo(NOW);
    assertThat(view.proposal()).isSameAs(proposal);
    assertThat(view.categoryName()).isEqualTo("Design");
  }

  @Test
  @DisplayName("a category that does not exist is not found and nothing is recorded")
  void aCategoryThatDoesNotExistIsNotFound() {
    UUID missing = UUID.randomUUID();
    when(categories.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    UPC, () -> useCase.execute(STUDENT, missing, "Figma", null, false)))
        .isInstanceOf(NoSuchElementException.class);
    verify(proposals, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("a name that does not fit is refused before the catalogue is read")
  void aNameThatDoesNotFitIsRefused() {
    assertThatThrownBy(() -> propose("ab", false)).isInstanceOf(SkillsRuleViolation.class);
    verify(catalogItems, never()).findVisible(any(), any());
    verify(proposals, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("skills that look like it stop the proposal and are listed")
  void skillsThatLookLikeItStopTheProposal() {
    CatalogItem nodeJs = inCatalogue("Node.js");
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of(nodeJs));

    assertThatThrownBy(() -> propose("NodeJS Avanzado", false)).isInstanceOf(SimilarSkillsFound.class);
    verify(proposals, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("the refusal for similar skills carries them so they can be shown")
  void theRefusalForSimilarSkillsCarriesThem() {
    CatalogItem python = inCatalogue("Programming in Python");
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE)).thenReturn(List.of(python));

    assertThatThrownBy(() -> propose("Python", false))
        .isInstanceOfSatisfying(
            SimilarSkillsFound.class,
            found -> {
              assertThat(found.getSimilar()).containsExactly(python);
              assertThat(found.getMessage()).contains("Programming in Python");
            });
  }

  @Test
  @DisplayName("once the student confirms theirs is a different one, it is recorded")
  void onceTheStudentConfirmsItIsRecorded() {
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE))
        .thenReturn(List.of(inCatalogue("Programming in Python")));

    propose("Python", true);

    verify(proposals).saveAndFlush(any(SkillProposal.class));
  }

  @Test
  @DisplayName("a name the catalogue already has is refused, and confirming does not change it")
  void aNameTheCatalogueAlreadyHasIsRefused() {
    when(catalogItems.findVisible(UPC, CatalogItemStatus.ACTIVE))
        .thenReturn(List.of(inCatalogue("Node.js")));

    assertThatThrownBy(() -> propose("NODEJS", true))
        .isInstanceOf(SkillsStateConflict.class)
        .isNotInstanceOf(SimilarSkillsFound.class)
        .hasMessageContaining("Node.js");
    verify(proposals, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("a proposal the student already has waiting is refused, and confirming does not change it")
  void aProposalTheStudentAlreadyHasWaitingIsRefused() {
    when(proposals.existsByTenantIdAndProposedByAndStatusAndNameIgnoreCase(
            UPC, STUDENT, ProposalStatus.PROPOSED, "Figma"))
        .thenReturn(true);

    assertThatThrownBy(() -> propose("Figma", true))
        .isInstanceOf(SkillsStateConflict.class)
        .isNotInstanceOf(SimilarSkillsFound.class);
    verify(proposals, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("two requests of the same proposal at once: the one the database refuses is a conflict")
  void twoRequestsAtOnceAreAConflict() {
    when(proposals.saveAndFlush(any(SkillProposal.class)))
        .thenThrow(new DataIntegrityViolationException("ux_skill_proposals_waiting"));

    assertThatThrownBy(() -> propose("Figma", false)).isInstanceOf(SkillsStateConflict.class);
  }
}
