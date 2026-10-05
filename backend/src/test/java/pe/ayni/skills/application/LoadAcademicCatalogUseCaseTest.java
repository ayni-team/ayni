package pe.ayni.skills.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase.CourseToLoad;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase.LoadedCourse;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase.Outcome;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.NotACoordinator;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/** US51, scenario 1: what loading a curriculum does with each course, and what stops it. */
class LoadAcademicCatalogUseCaseTest {

  private static final String UPC = "UPC";
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final UUID COORDINATOR = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();
  private static final UUID CATEGORY = UUID.randomUUID();

  private final IdentityApi identity = mock(IdentityApi.class);
  private final CatalogItemRepository catalogItems = mock(CatalogItemRepository.class);
  private final CategoryRepository categories = mock(CategoryRepository.class);
  private final LoadAcademicCatalogUseCase useCase =
      new LoadAcademicCatalogUseCase(
          new CoordinatorGuard(identity), catalogItems, categories, Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void aCoordinatorAStudentAndACategory() {
    when(identity.requireUser(COORDINATOR))
        .thenReturn(new UserView(COORDINATOR, UPC, UserRole.COORDINATOR, "c@upc.edu.pe", null, "C", null, null, null));
    when(identity.requireUser(STUDENT))
        .thenReturn(new UserView(STUDENT, UPC, UserRole.STUDENT, "s@upc.edu.pe", "U1", "S", null, null, null));
    when(categories.findByName(LoadAcademicCatalogUseCase.COURSES_CATEGORY))
        .thenReturn(Optional.of(new Category(CATEGORY, LoadAcademicCatalogUseCase.COURSES_CATEGORY, (short) 4)));
    when(catalogItems.findByTenantIdAndCourseCodeIn(any(), any())).thenReturn(List.of());
    when(catalogItems.save(any(CatalogItem.class))).thenAnswer(call -> call.getArgument(0));
  }

  private static CatalogItem held(String code, String name, String description) {
    return new CatalogItem(UUID.randomUUID(), CatalogScope.UNIVERSITY, UPC, CATEGORY, name, description, code, NOW);
  }

  private List<LoadedCourse> load(UUID asking, CourseToLoad... courses) {
    AtomicReference<List<LoadedCourse>> result = new AtomicReference<>();
    TenantContext.runAs(UPC, () -> result.set(useCase.execute(asking, List.of(courses))));
    return result.get();
  }

  @Test
  @DisplayName("a course the university does not have is created, active, under its code and in the courses category")
  void aNewCourseIsCreated() {
    List<LoadedCourse> loaded = load(COORDINATOR, new CourseToLoad("1ASI0657", "Software Architecture", "Styles"));

    assertThat(loaded).singleElement().extracting(LoadedCourse::outcome).isEqualTo(Outcome.CREATED);
    ArgumentCaptor<CatalogItem> saved = ArgumentCaptor.forClass(CatalogItem.class);
    verify(catalogItems).save(saved.capture());
    CatalogItem course = saved.getValue();
    assertThat(course.getScope()).isEqualTo(CatalogScope.UNIVERSITY);
    assertThat(course.getTenantId()).isEqualTo(UPC);
    assertThat(course.getCourseCode()).isEqualTo("1ASI0657");
    assertThat(course.getName()).isEqualTo("Software Architecture");
    assertThat(course.getDescription()).isEqualTo("Styles");
    assertThat(course.getCategoryId()).isEqualTo(CATEGORY);
    assertThat(course.isActive()).isTrue();
    verify(catalogItems).lockCoursesOf(UPC);
  }

  @Test
  @DisplayName("the courses category is created when missing, and asked for once however many courses are new")
  void theCategoryIsCreatedWhenMissing() {
    load(COORDINATOR, new CourseToLoad("A1", "One", null), new CourseToLoad("A2", "Two", null));

    verify(categories, org.mockito.Mockito.times(1))
        .createIfMissing(any(UUID.class), org.mockito.ArgumentMatchers.eq(LoadAcademicCatalogUseCase.COURSES_CATEGORY), org.mockito.ArgumentMatchers.anyShort());
  }

  @Test
  @DisplayName("a course already as listed is unchanged and not saved again")
  void aCourseAsListedIsUnchanged() {
    CatalogItem existing = held("1ASI0657", "Software Architecture", "Styles");
    when(catalogItems.findByTenantIdAndCourseCodeIn(any(), any())).thenReturn(List.of(existing));

    List<LoadedCourse> loaded = load(COORDINATOR, new CourseToLoad("1ASI0657", "Software Architecture", "Styles"));

    assertThat(loaded).singleElement().extracting(LoadedCourse::outcome).isEqualTo(Outcome.UNCHANGED);
    verify(catalogItems, never()).save(any());
    verifyNoInteractions(categories);
  }

  @Test
  @DisplayName("a course with another name or description takes the listed ones and keeps its code and identity")
  void aCourseIsUpdated() {
    CatalogItem existing = held("1ASI0657", "Old name", null);
    when(catalogItems.findByTenantIdAndCourseCodeIn(any(), any())).thenReturn(List.of(existing));

    List<LoadedCourse> loaded = load(COORDINATOR, new CourseToLoad("1ASI0657", "New name", "Now described"));

    assertThat(loaded).singleElement().extracting(LoadedCourse::outcome).isEqualTo(Outcome.UPDATED);
    assertThat(existing.getName()).isEqualTo("New name");
    assertThat(existing.getDescription()).isEqualTo("Now described");
    assertThat(existing.getCourseCode()).isEqualTo("1ASI0657");
    assertThat(existing.isActive()).isTrue();
  }

  @Test
  @DisplayName("a retired course is brought back, with the listed name")
  void aRetiredCourseIsReinstated() {
    CatalogItem existing = held("1ASI0657", "Software Architecture", null);
    existing.retire();
    when(catalogItems.findByTenantIdAndCourseCodeIn(any(), any())).thenReturn(List.of(existing));

    List<LoadedCourse> loaded = load(COORDINATOR, new CourseToLoad("1ASI0657", "Architecture II", null));

    assertThat(loaded).singleElement().extracting(LoadedCourse::outcome).isEqualTo(Outcome.REINSTATED);
    assertThat(existing.isActive()).isTrue();
    assertThat(existing.getName()).isEqualTo("Architecture II");
  }

  @Test
  @DisplayName("the answer keeps the order of the list and mixes the outcomes")
  void theOutcomesKeepTheOrder() {
    CatalogItem same = held("B", "Same", null);
    CatalogItem renamed = held("C", "Before", null);
    when(catalogItems.findByTenantIdAndCourseCodeIn(any(), any())).thenReturn(List.of(renamed, same));

    List<LoadedCourse> loaded =
        load(
            COORDINATOR,
            new CourseToLoad("A", "Brand new", null),
            new CourseToLoad("B", "Same", null),
            new CourseToLoad("C", "After", null));

    assertThat(loaded)
        .extracting(each -> each.item().getCourseCode(), LoadedCourse::outcome)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("A", Outcome.CREATED),
            org.assertj.core.groups.Tuple.tuple("B", Outcome.UNCHANGED),
            org.assertj.core.groups.Tuple.tuple("C", Outcome.UPDATED));
  }

  @Test
  @DisplayName("spaces around a code, a name or a description are dropped, and a blank description is none")
  void whatWasTypedIsTrimmed() {
    load(COORDINATOR, new CourseToLoad("  1ASI0657 ", "  Software Architecture ", "   "));

    ArgumentCaptor<CatalogItem> saved = ArgumentCaptor.forClass(CatalogItem.class);
    verify(catalogItems).save(saved.capture());
    assertThat(saved.getValue().getCourseCode()).isEqualTo("1ASI0657");
    assertThat(saved.getValue().getName()).isEqualTo("Software Architecture");
    assertThat(saved.getValue().getDescription()).isNull();
  }

  @Test
  @DisplayName("an empty list, a blank code or name, a long one or a repeated code is refused and nothing is touched")
  void aBadListIsRefusedWhole() {
    List<List<CourseToLoad>> bad =
        List.of(
            List.of(),
            List.of(new CourseToLoad("  ", "Name", null)),
            List.of(new CourseToLoad(null, "Name", null)),
            List.of(new CourseToLoad("A", " ", null)),
            List.of(new CourseToLoad("A".repeat(33), "Name", null)),
            List.of(new CourseToLoad("A", "N".repeat(161), null)),
            List.of(new CourseToLoad("A", "Name", "D".repeat(501))),
            List.of(new CourseToLoad("A1", "One", null), new CourseToLoad("a1", "Again", null)),
            List.of(new CourseToLoad("OK", "Fine", null), new CourseToLoad("A", "", null)));
    for (List<CourseToLoad> list : bad) {
      assertThatThrownBy(() -> TenantContext.runAs(UPC, () -> useCase.execute(COORDINATOR, list)))
          .isInstanceOf(SkillsRuleViolation.class);
    }
    assertThatThrownBy(() -> TenantContext.runAs(UPC, () -> useCase.execute(COORDINATOR, tooMany())))
        .isInstanceOf(SkillsRuleViolation.class);

    verifyNoInteractions(categories);
    verify(catalogItems, never()).save(any());
    verify(catalogItems, never()).lockCoursesOf(any());
  }

  private static List<CourseToLoad> tooMany() {
    List<CourseToLoad> list = new ArrayList<>();
    for (int i = 0; i < 1001; i++) {
      list.add(new CourseToLoad("C" + i, "Course " + i, null));
    }
    return list;
  }

  @Test
  @DisplayName("a student cannot load courses and nothing is saved")
  void aStudentCannotLoad() {
    assertThatThrownBy(() -> load(STUDENT, new CourseToLoad("A", "One", null))).isInstanceOf(NotACoordinator.class);

    verify(catalogItems, never()).save(any());
    verify(catalogItems, never()).lockCoursesOf(any());
  }
}
