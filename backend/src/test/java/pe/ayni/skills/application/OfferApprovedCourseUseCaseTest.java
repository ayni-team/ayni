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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.domain.model.SkillsStateConflict;
import pe.ayni.skills.infrastructure.AcademicSettingsRepository;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/** US13: offering a university course the tutor's academic record already clears. */
class OfferApprovedCourseUseCaseTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final UUID CATALOG_ITEM_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  private static final BigDecimal THRESHOLD = new BigDecimal("13.00");

  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final OfferApprovedCourseUseCase useCase =
      new OfferApprovedCourseUseCase(
          catalogItems,
          offeredSkills,
          identity,
          new TeachingThreshold(mock(AcademicSettingsRepository.class), identity),
          events,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static CatalogItem universityCourse(String courseCode) {
    return new CatalogItem(
        CATALOG_ITEM_ID, CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Fundamentos", null,
        courseCode, NOW);
  }

  private static CatalogItem globalTool() {
    return new CatalogItem(
        CATALOG_ITEM_ID, CatalogScope.GLOBAL, null, UUID.randomUUID(), "Python", null, null, NOW);
  }

  private OfferedSkill execute() {
    OfferedSkill[] result = new OfferedSkill[1];
    TenantContext.runAs(UPC, () -> result[0] = useCase.execute(TUTOR, CATALOG_ITEM_ID));
    return result[0];
  }

  @BeforeEach
  void tutorHasNoExistingOfferByDefault() {
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, CATALOG_ITEM_ID))
        .thenReturn(Optional.empty());
  }

  @Test
  @DisplayName("a tutor that already offers the item is refused")
  void aTutorThatAlreadyOffersTheItemIsRefused() {
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, CATALOG_ITEM_ID))
        .thenReturn(
            Optional.of(
                OfferedSkill.enableByAcademicRecord(
                    UUID.randomUUID(), UPC, TUTOR, CATALOG_ITEM_ID, THRESHOLD, THRESHOLD, NOW)));

    assertThatThrownBy(this::execute).isInstanceOf(SkillsStateConflict.class);

    verify(catalogItems, never()).lockByIdAndTenantVisibilityForShare(any(), any());
    verify(offeredSkills, never()).save(any());
  }

  @Test
  @DisplayName("a withdrawn course is offered again, on the same row, once the grade still clears")
  void aWithdrawnCourseIsOfferedAgain() {
    OfferedSkill withdrawn =
        OfferedSkill.enableByAcademicRecord(
            UUID.randomUUID(), UPC, TUTOR, CATALOG_ITEM_ID, THRESHOLD, THRESHOLD, NOW.minusSeconds(3600));
    withdrawn.withdraw(NOW.minusSeconds(60));
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, CATALOG_ITEM_ID))
        .thenReturn(Optional.of(withdrawn));
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(universityCourse("1ASI0657")));
    when(identity.approvedCourses(TUTOR))
        .thenReturn(
            List.of(new ApprovedCourseView("1ASI0657", "Fundamentos", new BigDecimal("16.00"), "2026-1")));
    when(identity.requireTenant(UPC)).thenReturn(tenant(THRESHOLD));

    OfferedSkill skill = execute();

    assertThat(skill).isSameAs(withdrawn);
    assertThat(skill.isEnabled()).isTrue();
    assertThat(skill.getAccreditedGrade()).isEqualByComparingTo("16.00");
    verify(events).publishEvent(any(SkillEnabled.class));
  }

  @Test
  @DisplayName("a withdrawn course whose grade no longer clears is refused and stays withdrawn")
  void aWithdrawnCourseWhoseGradeNoLongerClearsIsRefused() {
    OfferedSkill withdrawn =
        OfferedSkill.enableByAcademicRecord(
            UUID.randomUUID(), UPC, TUTOR, CATALOG_ITEM_ID, THRESHOLD, THRESHOLD, NOW.minusSeconds(3600));
    withdrawn.withdraw(NOW.minusSeconds(60));
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, CATALOG_ITEM_ID))
        .thenReturn(Optional.of(withdrawn));
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(universityCourse("1ASI0657")));
    when(identity.approvedCourses(TUTOR))
        .thenReturn(
            List.of(new ApprovedCourseView("1ASI0657", "Fundamentos", new BigDecimal("14.00"), "2026-1")));
    // The university raised the bar after the first offer.
    when(identity.requireTenant(UPC)).thenReturn(tenant(new BigDecimal("15.00")));

    assertThatThrownBy(this::execute).isInstanceOf(SkillsRuleViolation.class);

    assertThat(withdrawn.isEnabled()).isFalse();
    verify(events, never()).publishEvent(any());
  }

  @Test
  @DisplayName("a grade that reaches the threshold enables the skill")
  void gradeReachingTheThresholdEnablesTheSkill() {
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(universityCourse("1ASI0657")));
    when(identity.approvedCourses(TUTOR))
        .thenReturn(List.of(new ApprovedCourseView("1ASI0657", "Fundamentos", THRESHOLD, "2026-1")));
    when(identity.requireTenant(UPC)).thenReturn(tenant(THRESHOLD));
    when(offeredSkills.save(any())).thenAnswer(call -> call.getArgument(0));

    OfferedSkill skill = execute();

    assertThat(skill.isEnabled()).isTrue();
    verify(offeredSkills).save(any());
    verify(events).publishEvent(any(SkillEnabled.class));
  }

  @Test
  @DisplayName("a grade below the threshold is refused and nothing is saved")
  void gradeBelowTheThresholdIsRefused() {
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(universityCourse("1ASI0657")));
    when(identity.approvedCourses(TUTOR))
        .thenReturn(
            List.of(
                new ApprovedCourseView("1ASI0657", "Fundamentos", new BigDecimal("12.00"), "2026-1")));
    when(identity.requireTenant(UPC)).thenReturn(tenant(THRESHOLD));

    assertThatThrownBy(this::execute).isInstanceOf(SkillsRuleViolation.class);

    verify(offeredSkills, never()).save(any());
    verify(events, never()).publishEvent(any());
  }

  @Test
  @DisplayName("a course missing from the academic record is refused")
  void aCourseMissingFromTheAcademicRecordIsRefused() {
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(universityCourse("1ASI0657")));
    when(identity.approvedCourses(TUTOR)).thenReturn(List.of());

    assertThatThrownBy(this::execute).isInstanceOf(SkillsRuleViolation.class);

    verify(offeredSkills, never()).save(any());
  }

  @Test
  @DisplayName("a global tool is refused before consulting the academic record")
  void aGlobalToolIsRefusedBeforeConsultingTheAcademicRecord() {
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(globalTool()));

    assertThatThrownBy(this::execute)
        .isInstanceOf(SkillsRuleViolation.class)
        .hasMessageContaining("reviewed evidence");

    verify(identity, never()).approvedCourses(any());
    verify(offeredSkills, never()).save(any());
  }

  @Test
  @DisplayName("a retired course is refused before consulting the academic record")
  void aRetiredCourseIsRefused() {
    CatalogItem retired = universityCourse("1ASI0657");
    retired.retire();
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(retired));

    assertThatThrownBy(this::execute)
        .isInstanceOf(SkillsRuleViolation.class)
        .hasMessageContaining("retired");

    verify(identity, never()).approvedCourses(any());
    verify(offeredSkills, never()).save(any());
  }

  @Test
  @DisplayName("a withdrawn tool with accepted evidence is offered again, with no academic record")
  void aWithdrawnToolWithAcceptedEvidenceIsOfferedAgain() {
    OfferedSkill withdrawn = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, CATALOG_ITEM_ID, NOW);
    withdrawn.approveByReviewedEvidence(NOW);
    withdrawn.withdraw(NOW);
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, CATALOG_ITEM_ID))
        .thenReturn(Optional.of(withdrawn));
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC)).thenReturn(Optional.of(globalTool()));

    OfferedSkill skill = execute();

    assertThat(skill).isSameAs(withdrawn);
    assertThat(skill.isEnabled()).isTrue();
    verify(events).publishEvent(any(SkillEnabled.class));
    verify(identity, never()).approvedCourses(any());
  }

  @Test
  @DisplayName("a global tool the tutor never had accepted is still refused")
  void aGlobalToolNeverAcceptedIsStillRefused() {
    OfferedSkill rejected = OfferedSkill.requestValidation(UUID.randomUUID(), UPC, TUTOR, CATALOG_ITEM_ID, NOW);
    rejected.rejectEvidence(NOW);
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, CATALOG_ITEM_ID))
        .thenReturn(Optional.of(rejected));

    // A rejected tool is not withdrawn, so it is the conflict that answers, before any item is read.
    assertThatThrownBy(this::execute).isInstanceOf(SkillsStateConflict.class);

    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("an unknown catalog item is refused")
  void anUnknownCatalogItemIsRefused() {
    when(catalogItems.lockByIdAndTenantVisibilityForShare(CATALOG_ITEM_ID, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(this::execute).isInstanceOf(NoSuchElementException.class);
  }

  private static TenantView tenant(BigDecimal threshold) {
    return new TenantView(UPC, "UPC", "America/Lima", threshold, true);
  }
}
