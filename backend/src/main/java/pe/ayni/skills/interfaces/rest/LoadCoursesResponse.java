package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase.LoadedCourse;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase.Outcome;

/**
 * What loading a curriculum did, in the order it was given.
 *
 * @param created courses the university did not have
 * @param updated courses whose name or description changed
 * @param reinstated courses that were retired and are active again
 * @param unchanged courses that were already as listed
 */
@Schema(name = "LoadedCourses", description = "What loading the curriculum did")
public record LoadCoursesResponse(
    @Schema(example = "38") long created,
    @Schema(example = "2") long updated,
    @Schema(example = "1") long reinstated,
    @Schema(example = "4") long unchanged,
    List<Course> courses) {

  /** A course, and what happened to it. */
  @Schema(name = "LoadedCourse")
  public record Course(
      AcademicCourseResponse course,
      @Schema(allowableValues = {"CREATED", "UPDATED", "REINSTATED", "UNCHANGED"}, example = "CREATED")
          String outcome) {}

  static LoadCoursesResponse of(List<LoadedCourse> loaded) {
    return new LoadCoursesResponse(
        count(loaded, Outcome.CREATED),
        count(loaded, Outcome.UPDATED),
        count(loaded, Outcome.REINSTATED),
        count(loaded, Outcome.UNCHANGED),
        loaded.stream()
            .map(each -> new Course(AcademicCourseResponse.of(each.item()), each.outcome().name()))
            .toList());
  }

  private static long count(List<LoadedCourse> loaded, Outcome outcome) {
    return loaded.stream().filter(each -> each.outcome() == outcome).count();
  }
}
