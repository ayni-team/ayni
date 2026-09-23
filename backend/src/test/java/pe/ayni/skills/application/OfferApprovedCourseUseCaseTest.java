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
import pe.ayni.shared.events.SkillEnabled;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
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
          catalogItems, offeredSkills, identity, events, Clock.fixed(NOW, ZoneOffset.UTC));

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

  @Test
  @DisplayName("a grade that reaches the threshold enables the skill")
  void gradeReachingTheThresholdEnablesTheSkill() {
    when(catalogItems.findByIdAndTenantVisibility(CATALOG_ITEM_ID, UPC))
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
    when(catalogItems.findByIdAndTenantVisibility(CATALOG_ITEM_ID, UPC))
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
    when(catalogItems.findByIdAndTenantVisibility(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(universityCourse("1ASI0657")));
    when(identity.approvedCourses(TUTOR)).thenReturn(List.of());

    assertThatThrownBy(this::execute).isInstanceOf(SkillsRuleViolation.class);

    verify(offeredSkills, never()).save(any());
  }

  @Test
  @DisplayName("a global tool is refused before consulting the academic record")
  void aGlobalToolIsRefusedBeforeConsultingTheAcademicRecord() {
    when(catalogItems.findByIdAndTenantVisibility(CATALOG_ITEM_ID, UPC))
        .thenReturn(Optional.of(globalTool()));

    assertThatThrownBy(this::execute)
        .isInstanceOf(SkillsRuleViolation.class)
        .hasMessageContaining("reviewed evidence");

    verify(identity, never()).approvedCourses(any());
    verify(offeredSkills, never()).save(any());
  }

  @Test
  @DisplayName("an unknown catalog item is refused")
  void anUnknownCatalogItemIsRefused() {
    when(catalogItems.findByIdAndTenantVisibility(CATALOG_ITEM_ID, UPC)).thenReturn(Optional.empty());

    assertThatThrownBy(this::execute).isInstanceOf(NoSuchElementException.class);
  }

  private static TenantView tenant(BigDecimal threshold) {
    return new TenantView(UPC, "UPC", "America/Lima", threshold, true);
  }
}
