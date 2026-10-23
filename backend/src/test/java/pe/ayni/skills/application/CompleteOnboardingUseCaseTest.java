package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.AcademicSettingsRepository;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US40: the last step of the onboarding asks identity for the step before it declares anything.
 *
 * <p>Only identity is mocked, as the other module; the declaring use cases are the real ones, over
 * mocked repositories.
 */
class CompleteOnboardingUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID ITEM = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

  private final LearningInterestRepository learningInterests = mock(LearningInterestRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final DeclareInterestsUseCase declareInterests =
      new DeclareInterestsUseCase(
          learningInterests,
          catalogItems,
          new OfferApprovedCourseUseCase(
              catalogItems,
              offeredSkills,
              identity,
              new TeachingThreshold(mock(AcademicSettingsRepository.class), identity),
              mock(ApplicationEventPublisher.class),
              clock),
          clock);
  private final CompleteOnboardingUseCase useCase =
      new CompleteOnboardingUseCase(
          identity, declareInterests, learningInterests, catalogItems, offeredSkills);

  private CompleteOnboardingUseCase.Onboarded asUpc(List<UUID> learning, List<UUID> teaching) {
    CompleteOnboardingUseCase.Onboarded[] result = new CompleteOnboardingUseCase.Onboarded[1];
    TenantContext.runAs(UPC, () -> result[0] = useCase.execute(STUDENT, learning, teaching));
    return result[0];
  }

  private static UserView a(UserRole role) {
    return new UserView(STUDENT, UPC, role, null, null, "Ana", null, null, null);
  }

  @Test
  @DisplayName("the step is completed before the interests are declared")
  void theStepIsCompletedBeforeTheInterestsAreDeclared() {
    CatalogItem item =
        new CatalogItem(ITEM, CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Physics", null, "FI101", NOW);
    when(identity.requireUser(STUDENT)).thenReturn(a(UserRole.STUDENT));
    when(catalogItems.findAllByIdInAndTenantVisibility(any(), eq(UPC))).thenReturn(List.of(item));
    when(learningInterests.save(any())).thenAnswer(call -> call.getArgument(0));

    CompleteOnboardingUseCase.Onboarded onboarded = asUpc(List.of(ITEM), List.of());

    assertThat(onboarded.learning()).containsExactly(item);
    assertThat(onboarded.teaching()).isEmpty();
    InOrder order = inOrder(learningInterests, identity);
    order.verify(learningInterests).lockInterestsOf(UPC, STUDENT);
    order.verify(identity).completeOnboarding(STUDENT);
    order.verify(learningInterests).save(any());
  }

  @Test
  @DisplayName("a step refused by identity is a conflict and nothing is declared")
  void aStepRefusedByIdentityIsAConflict() {
    when(identity.requireUser(STUDENT)).thenReturn(a(UserRole.STUDENT));
    doThrow(new IllegalStateException("The onboarding is at step ACADEMIC_RECORD"))
        .when(identity)
        .completeOnboarding(STUDENT);

    assertThatThrownBy(() -> asUpc(List.of(ITEM), List.of(ITEM)))
        .isInstanceOf(SkillsStateConflict.class)
        .hasMessageContaining("ACADEMIC_RECORD");
    verify(learningInterests, never()).save(any());
    verify(offeredSkills, never()).save(any());
  }

  @Test
  @DisplayName("a coordinator has no onboarding")
  void aCoordinatorHasNoOnboarding() {
    when(identity.requireUser(STUDENT)).thenReturn(a(UserRole.COORDINATOR));

    assertThatThrownBy(() -> asUpc(List.of(), List.of())).isInstanceOf(SkillsStateConflict.class);
    verify(identity, never()).completeOnboarding(any());
  }
}
