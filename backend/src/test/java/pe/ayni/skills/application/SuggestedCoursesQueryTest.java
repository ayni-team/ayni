package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.ayni.identity.ApprovedCourseView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.TenantView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.CatalogItemStatus;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.OfferedSkillRepository;

class SuggestedCoursesQueryTest {

  private static final String UPC = "UPC";
  private static final UUID TUTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  private static final BigDecimal THRESHOLD = new BigDecimal("13.00");

  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final OfferedSkillRepository offeredSkills = mock(OfferedSkillRepository.class);
  private final IdentityApi identity = mock(IdentityApi.class);
  private final SuggestedCoursesQuery query =
      new SuggestedCoursesQuery(catalogItems, offeredSkills, identity);

  private static CatalogItem course(String courseCode) {
    return new CatalogItem(
        UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, UUID.randomUUID(), "Course " + courseCode,
        null, courseCode, NOW);
  }

  private static ApprovedCourseView approved(String courseCode, String grade) {
    return new ApprovedCourseView(courseCode, "Course " + courseCode, new BigDecimal(grade), "2026-1");
  }

  private void catalogueHas(CatalogItem... items) {
    when(catalogItems.findByTenantIdAndCourseCodeInAndStatus(
            eq(UPC), anyCollection(), eq(CatalogItemStatus.ACTIVE)))
        .thenReturn(List.of(items));
  }

  private List<SuggestedCoursesQuery.SuggestedCourse> forTutor() {
    Object[] result = new Object[1];
    TenantContext.runAs(UPC, () -> result[0] = query.forTutor(TUTOR));
    @SuppressWarnings("unchecked")
    List<SuggestedCoursesQuery.SuggestedCourse> typed =
        (List<SuggestedCoursesQuery.SuggestedCourse>) result[0];
    return typed;
  }

  @BeforeEach
  void theUniversityAsksForThirteen() {
    when(identity.requireTenant(UPC))
        .thenReturn(new TenantView(UPC, "UPC", "America/Lima", THRESHOLD, true));
    when(offeredSkills.findAllCatalogItemIds(UPC, TUTOR)).thenReturn(List.of());
  }

  @Test
  @DisplayName("a course approved above the threshold and not yet offered is suggested")
  void aCourseApprovedAndNotYetOfferedIsSuggested() {
    CatalogItem item = course("1ASI0657");
    when(identity.approvedCourses(TUTOR)).thenReturn(List.of(approved("1ASI0657", "15.00")));
    catalogueHas(item);

    assertThat(forTutor())
        .singleElement()
        .satisfies(
            suggestion -> {
              assertThat(suggestion.item()).isEqualTo(item);
              assertThat(suggestion.grade()).isEqualByComparingTo("15.00");
            });
  }

  @Test
  @DisplayName("a course approved below the threshold is not suggested")
  void aCourseBelowTheThresholdIsNotSuggested() {
    when(identity.approvedCourses(TUTOR)).thenReturn(List.of(approved("1MAT0101", "11.00")));

    assertThat(forTutor()).isEmpty();
    verify(catalogItems, never()).findByTenantIdAndCourseCodeInAndStatus(any(), any(), any());
  }

  @Test
  @DisplayName("only the courses that clear the threshold are looked up in the catalogue")
  void onlyClearingCoursesAreLookedUp() {
    when(identity.approvedCourses(TUTOR))
        .thenReturn(List.of(approved("1ASI0657", "15.00"), approved("1MAT0101", "11.00")));
    catalogueHas();

    forTutor();

    verify(catalogItems)
        .findByTenantIdAndCourseCodeInAndStatus(
            eq(UPC), argThat(codes -> Set.copyOf(codes).equals(Set.of("1ASI0657"))),
            eq(CatalogItemStatus.ACTIVE));
  }

  @Test
  @DisplayName("a course taken twice is suggested once, with its best grade")
  void aCourseTakenTwiceIsSuggestedWithItsBestGrade() {
    CatalogItem item = course("1ASI0616");
    when(identity.approvedCourses(TUTOR))
        .thenReturn(List.of(approved("1ASI0616", "13.50"), approved("1ASI0616", "17.00")));
    catalogueHas(item);

    assertThat(forTutor())
        .singleElement()
        .satisfies(suggestion -> assertThat(suggestion.grade()).isEqualByComparingTo("17.00"));
  }

  @Test
  @DisplayName("an approved course with no catalog counterpart is left out")
  void anApprovedCourseWithNoCatalogCounterpartIsLeftOut() {
    when(identity.approvedCourses(TUTOR)).thenReturn(List.of(approved("XYZ999", "15.00")));
    catalogueHas();

    assertThat(forTutor()).isEmpty();
  }

  @Test
  @DisplayName("a course already offered, even withdrawn, is not suggested again")
  void aCourseAlreadyOfferedEvenWithdrawnIsNotSuggestedAgain() {
    CatalogItem item = course("1ASI0657");
    when(identity.approvedCourses(TUTOR)).thenReturn(List.of(approved("1ASI0657", "15.00")));
    catalogueHas(item);
    when(offeredSkills.findAllCatalogItemIds(UPC, TUTOR)).thenReturn(List.of(item.getId()));

    assertThat(forTutor()).isEmpty();
  }
}
