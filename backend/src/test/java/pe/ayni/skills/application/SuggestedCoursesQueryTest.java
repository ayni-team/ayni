package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.OfferedSkill;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

/** US13, scenarios 2 and 4: what shows up as a course the tutor could offer, and what does not. */
class SuggestedCoursesQueryTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private final SuggestedCoursesQuery query =
      new SuggestedCoursesQuery(catalogItems, offeredSkills, identity);

  private static CatalogItem course(String courseCode) {
    return new CatalogItem(
        UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Fundamentos", null,
        courseCode, NOW);
  }

  private List<SuggestedCoursesQuery.SuggestedCourse> forTutor() {
    Object[] result = new Object[1];
    TenantContext.runAs(UPC, () -> result[0] = query.forTutor(TUTOR));
    @SuppressWarnings("unchecked")
    List<SuggestedCoursesQuery.SuggestedCourse> typed =
        (List<SuggestedCoursesQuery.SuggestedCourse>) result[0];
    return typed;
  }

  @Test
  @DisplayName("a course approved and not yet offered is suggested")
  void aCourseApprovedAndNotYetOfferedIsSuggested() {
    CatalogItem item = course("1ASI0657");
    when(identity.approvedCourses(TUTOR))
        .thenReturn(
            List.of(new ApprovedCourseView("1ASI0657", "Fundamentos", new BigDecimal("15.00"), "2026-1")));
    when(catalogItems.findByTenantIdAndCourseCode(UPC, "1ASI0657")).thenReturn(Optional.of(item));
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, item.getId()))
        .thenReturn(Optional.empty());

    assertThat(forTutor()).extracting(SuggestedCoursesQuery.SuggestedCourse::item).containsExactly(item);
  }

  @Test
  @DisplayName("an approved course with no catalog counterpart is left out")
  void anApprovedCourseWithNoCatalogCounterpartIsLeftOut() {
    when(identity.approvedCourses(TUTOR))
        .thenReturn(
            List.of(new ApprovedCourseView("XYZ999", "Curso sin catálogo", new BigDecimal("15.00"), "2026-1")));
    when(catalogItems.findByTenantIdAndCourseCode(UPC, "XYZ999")).thenReturn(Optional.empty());

    assertThat(forTutor()).isEmpty();
  }

  @Test
  @DisplayName("a course already offered, even withdrawn, is not suggested again")
  void aCourseAlreadyOfferedEvenWithdrawnIsNotSuggestedAgain() {
    CatalogItem item = course("1ASI0657");
    when(identity.approvedCourses(TUTOR))
        .thenReturn(
            List.of(new ApprovedCourseView("1ASI0657", "Fundamentos", new BigDecimal("15.00"), "2026-1")));
    when(catalogItems.findByTenantIdAndCourseCode(UPC, "1ASI0657")).thenReturn(Optional.of(item));
    OfferedSkill existing =
        OfferedSkill.enableByAcademicRecord(
            UUID.randomUUID(), UPC, TUTOR, item.getId(), new BigDecimal("15.00"),
            new BigDecimal("13.00"), NOW);
    when(offeredSkills.findByTenantIdAndTutorIdAndCatalogItemId(UPC, TUTOR, item.getId()))
        .thenReturn(Optional.of(existing));

    assertThat(forTutor()).isEmpty();
  }
}
