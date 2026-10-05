package pe.ayni.skills.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.domain.model.CatalogItem;
import pe.ayni.skills.domain.model.Category;
import pe.ayni.skills.domain.model.SkillsRuleViolation;
import pe.ayni.skills.infrastructure.CatalogItemRepository;
import pe.ayni.skills.infrastructure.CategoryRepository;

/**
 * US51, scenario 1: a coordinator loads the courses of their curriculum, and they become available
 * for the students of the university to search for and to offer.
 *
 * <p>Loading adds and updates, and never removes: a course that is not in the list stays as it was,
 * because taking one out is a decision with consequences for the tutors who offer it, and has its
 * own step, retiring a course. Each course in the list ends in one of four ways:
 *
 * <ul>
 *   <li>{@link Outcome#CREATED}: the university did not have it, and now does.
 *   <li>{@link Outcome#UPDATED}: it had it under another name or description, and now they match the
 *       list.
 *   <li>{@link Outcome#REINSTATED}: it had retired it, and the list brings it back. Tutors who offered
 *       it before offer it anew.
 *   <li>{@link Outcome#UNCHANGED}: it was already there as listed.
 * </ul>
 *
 * <p>The course code is what the academic system reports on an approved course, so it is what a
 * student's record is matched with and it is never changed by loading. All or nothing: a list with a
 * repeated code or an empty name is refused before anything is saved.
 */
@Service
public class LoadAcademicCatalogUseCase {

  /** The category the courses of every university are grouped under. */
  public static final String COURSES_CATEGORY = "University courses";

  private static final short COURSES_CATEGORY_ORDER = 4;
  private static final int MOST_COURSES_AT_ONCE = 1000;

  private final CoordinatorGuard coordinators;
  private final CatalogItemRepository catalogItems;
  private final CategoryRepository categories;
  private final Clock clock;

  LoadAcademicCatalogUseCase(
      CoordinatorGuard coordinators,
      CatalogItemRepository catalogItems,
      CategoryRepository categories,
      Clock clock) {
    this.coordinators = coordinators;
    this.catalogItems = catalogItems;
    this.categories = categories;
    this.clock = clock;
  }

  /** A course as the university lists it. */
  public record CourseToLoad(String code, String name, String description) {}

  public enum Outcome {
    CREATED,
    UPDATED,
    REINSTATED,
    UNCHANGED
  }

  public record LoadedCourse(CatalogItem item, Outcome outcome) {}

  /**
   * @param courses the curriculum, in the order the coordinator gave it
   * @throws pe.ayni.skills.domain.model.NotACoordinator when the person is not a coordinator
   * @throws SkillsRuleViolation when the list is empty or too long, a code or a name is blank or too
   *     long, or a code is repeated
   */
  @Transactional
  public List<LoadedCourse> execute(UUID coordinatorId, List<CourseToLoad> courses) {
    Objects.requireNonNull(coordinatorId, "coordinatorId must not be null");
    Objects.requireNonNull(courses, "courses must not be null");
    List<CourseToLoad> listed = tidy(courses);

    coordinators.require(coordinatorId);
    String tenantId = TenantContext.require();

    // One coordinator at a time per university: see the lock.
    catalogItems.lockCoursesOf(tenantId);

    Map<String, CatalogItem> held = new HashMap<>();
    catalogItems
        .findByTenantIdAndCourseCodeIn(tenantId, listed.stream().map(CourseToLoad::code).toList())
        .forEach(item -> held.put(item.getCourseCode(), item));

    UUID category = null;
    Instant now = clock.instant();
    List<LoadedCourse> result = new ArrayList<>();
    for (CourseToLoad course : listed) {
      CatalogItem item = held.get(course.code());
      if (item == null) {
        if (category == null) {
          category = coursesCategory();
        }
        item =
            catalogItems.save(
                new CatalogItem(
                    UUID.randomUUID(),
                    CatalogScope.UNIVERSITY,
                    tenantId,
                    category,
                    course.name(),
                    course.description(),
                    course.code(),
                    now));
        result.add(new LoadedCourse(item, Outcome.CREATED));
        continue;
      }
      boolean reinstated = !item.isActive();
      boolean changed =
          !item.getName().equals(course.name())
              || !Objects.equals(item.getDescription(), course.description());
      if (reinstated) {
        item.reinstate();
      }
      if (changed) {
        item.describe(course.name(), course.description());
      }
      result.add(
          new LoadedCourse(
              item, reinstated ? Outcome.REINSTATED : changed ? Outcome.UPDATED : Outcome.UNCHANGED));
    }
    return result;
  }

  private UUID coursesCategory() {
    categories.createIfMissing(UUID.randomUUID(), COURSES_CATEGORY, COURSES_CATEGORY_ORDER);
    return categories.findByName(COURSES_CATEGORY).map(Category::getId).orElseThrow();
  }

  /** Trims what was typed and refuses what cannot be a curriculum, before anything is read or written. */
  private static List<CourseToLoad> tidy(List<CourseToLoad> courses) {
    if (courses.isEmpty()) {
      throw new SkillsRuleViolation("the list of courses is empty");
    }
    if (courses.size() > MOST_COURSES_AT_ONCE) {
      throw new SkillsRuleViolation(
          "at most %d courses can be loaded at once".formatted(MOST_COURSES_AT_ONCE));
    }
    Set<String> seen = new HashSet<>();
    List<CourseToLoad> tidy = new ArrayList<>();
    for (CourseToLoad course : courses) {
      String code = course.code() == null ? "" : course.code().trim();
      String name = course.name() == null ? "" : course.name().trim();
      String description =
          course.description() == null || course.description().isBlank()
              ? null
              : course.description().trim();
      if (code.isEmpty() || code.length() > 32) {
        throw new SkillsRuleViolation("the code of a course takes 1 to 32 characters");
      }
      if (name.isEmpty() || name.length() > 160) {
        throw new SkillsRuleViolation("the name of course %s takes 1 to 160 characters".formatted(code));
      }
      if (description != null && description.length() > 500) {
        throw new SkillsRuleViolation("the description of course %s takes at most 500 characters".formatted(code));
      }
      // A code that differs only in case is the same typing mistake, not another course.
      if (!seen.add(code.toLowerCase(Locale.ROOT))) {
        throw new SkillsRuleViolation("the course %s is listed twice".formatted(code));
      }
      tidy.add(new CourseToLoad(code, name, description));
    }
    return tidy;
  }
}
