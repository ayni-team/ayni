package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.LearningInterest;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.LearningInterestRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/**
 * US40: registering what a student needs help with, and enabling what they can already teach,
 * during the initial configuration.
 *
 * <p>{@link OfferApprovedCourseUseCase} is built for real here rather than mocked, wired to mocked
 * repositories and identity underneath: mocking a concrete class needs bytecode instrumentation
 * that some JDKs (this one included) refuse to perform, and asserting through the real object is
 * the more faithful test anyway.
 */
class DeclareInterestsUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID ITEM_A = UUID.randomUUID();
  private static final UUID ITEM_B = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  private static final BigDecimal THRESHOLD = new BigDecimal("13.00");

  private final LearningInterestRepository learningInterests = mock(LearningInterestRepository.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private final OfferApprovedCourseUseCase offerApprovedCourse =
      new OfferApprovedCourseUseCase(
          catalogItems,
          offeredSkills,
          identity,
          mock(ApplicationEventPublisher.class),
          Clock.fixed(NOW, ZoneOffset.UTC));
  private final DeclareInterestsUseCase useCase =
      new DeclareInterestsUseCase(
          learningInterests, catalogItems, offerApprovedCourse, Clock.fixed(NOW, ZoneOffset.UTC));

  private <T> T asUpc(java.util.function.Supplier<T> work) {
    Object[] result = new Object[1];
    TenantContext.runAs(UPC, () -> result[0] = work.get());
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }

  private static CatalogItem universityCourse(UUID id, String courseCode) {
    return new CatalogItem(
        id, CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Course " + courseCode, null, courseCode, NOW);
  }

  /** Wires the mocks so offering {@code item} for {@code grade} succeeds through the real use case. */
  private void approvedFor(UUID itemId, String courseCode, BigDecimal grade) {
    when(catalogItems.findByIdAndTenantVisibility(itemId, UPC))
        .thenReturn(Optional.of(universityCourse(itemId, courseCode)));
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, STUDENT, itemId))
        .thenReturn(Optional.empty());
    when(offeredSkills.save(any())).thenAnswer(call -> call.getArgument(0));
    when(identity.approvedCourses(STUDENT))
        .thenReturn(List.of(new ApprovedCourseView(courseCode, "Course " + courseCode, grade, "2026-1")));
    when(identity.requireTenant(UPC)).thenReturn(new TenantView(UPC, "UPC", "America/Lima", THRESHOLD, true));
  }

  @Test
  @DisplayName("a new interest is saved")
  void aNewInterestIsSaved() {
    when(catalogItems.findAllById(any())).thenReturn(List.of(universityCourse(ITEM_A, "1ASI0657")));
    when(learningInterests.findByTenantIdAndStudentId(UPC, STUDENT)).thenReturn(List.of());
    when(learningInterests.save(any())).thenAnswer(call -> call.getArgument(0));

    List<LearningInterest> result =
        asUpc(() -> useCase.declareLearningInterests(STUDENT, List.of(ITEM_A)));

    assertThat(result).hasSize(1);
    verify(learningInterests).save(any());
  }

  @Test
  @DisplayName("declaring the same interest twice does not insert a second row")
  void declaringTheSameInterestTwiceDoesNotInsertASecondRow() {
    LearningInterest existing = new LearningInterest(UUID.randomUUID(), UPC, STUDENT, ITEM_A, NOW);
    when(catalogItems.findAllById(any())).thenReturn(List.of(universityCourse(ITEM_A, "1ASI0657")));
    when(learningInterests.findByTenantIdAndStudentId(UPC, STUDENT)).thenReturn(List.of(existing));

    List<LearningInterest> result =
        asUpc(() -> useCase.declareLearningInterests(STUDENT, List.of(ITEM_A)));

    assertThat(result).containsExactly(existing);
    verify(learningInterests, never()).save(any());
  }

  @Test
  @DisplayName("an interest in another university's course is refused and nothing is saved")
  void anInterestInAnotherUniversitysCourseIsRefused() {
    CatalogItem foreign =
        new CatalogItem(
            ITEM_B, CatalogScope.UNIVERSITY, "UTEC", UUID.randomUUID(), "Course", null, "UT100", NOW);
    when(catalogItems.findAllById(any()))
        .thenReturn(List.of(universityCourse(ITEM_A, "1ASI0657"), foreign));

    assertThatThrownBy(
            () -> asUpc(() -> useCase.declareLearningInterests(STUDENT, List.of(ITEM_A, ITEM_B))))
        .isInstanceOf(NoSuchElementException.class);
    verify(learningInterests, never()).save(any());
  }

  @Test
  @DisplayName("an item the academic record clears is enabled")
  void anItemTheAcademicRecordClearsIsEnabled() {
    approvedFor(ITEM_A, "1ASI0657", new BigDecimal("15.00"));

    List<OfferedSkill> result = asUpc(() -> useCase.declareTeachingInterests(STUDENT, List.of(ITEM_A)));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCatalogItemId()).isEqualTo(ITEM_A);
    assertThat(result.get(0).isEnabled()).isTrue();
  }

  @Test
  @DisplayName("an item already offered is skipped, not refused")
  void anItemAlreadyOfferedIsSkippedNotRefused() {
    when(catalogItems.findByIdAndTenantVisibility(ITEM_A, UPC))
        .thenReturn(Optional.of(universityCourse(ITEM_A, "1ASI0657")));
    OfferedSkill existing =
        OfferedSkill.enableByAcademicRecord(
            UUID.randomUUID(), UPC, STUDENT, ITEM_A, new BigDecimal("15.00"), THRESHOLD, NOW);
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, STUDENT, ITEM_A))
        .thenReturn(Optional.of(existing));

    assertThat(asUpc(() -> useCase.declareTeachingInterests(STUDENT, List.of(ITEM_A)))).isEmpty();
  }

  @Test
  @DisplayName("one item failing does not stop the rest from being enabled")
  void oneItemFailingDoesNotStopTheRestFromBeingEnabled() {
    // ITEM_A: below the threshold, refused. ITEM_B: clears it, enabled.
    when(catalogItems.findByIdAndTenantVisibility(ITEM_A, UPC))
        .thenReturn(Optional.of(universityCourse(ITEM_A, "1MAT0101")));
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, STUDENT, ITEM_A))
        .thenReturn(Optional.empty());

    when(catalogItems.findByIdAndTenantVisibility(ITEM_B, UPC))
        .thenReturn(Optional.of(universityCourse(ITEM_B, "1ASI0616")));
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, STUDENT, ITEM_B))
        .thenReturn(Optional.empty());
    when(offeredSkills.save(any())).thenAnswer(call -> call.getArgument(0));

    when(identity.approvedCourses(STUDENT))
        .thenReturn(
            List.of(
                new ApprovedCourseView("1MAT0101", "Cálculo I", new BigDecimal("11.00"), "2026-1"),
                new ApprovedCourseView("1ASI0616", "Base de Datos I", new BigDecimal("15.00"), "2026-1")));
    when(identity.requireTenant(UPC)).thenReturn(new TenantView(UPC, "UPC", "America/Lima", THRESHOLD, true));

    List<OfferedSkill> result =
        asUpc(() -> useCase.declareTeachingInterests(STUDENT, List.of(ITEM_A, ITEM_B)));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCatalogItemId()).isEqualTo(ITEM_B);
  }
}
