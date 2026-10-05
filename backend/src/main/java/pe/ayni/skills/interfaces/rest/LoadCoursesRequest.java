package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import pe.ayni.skills.application.LoadAcademicCatalogUseCase.CourseToLoad;

/**
 * The courses of a curriculum.
 *
 * @param courses at most 1000 at once, each with a code that appears once in the list
 */
@Schema(name = "LoadCoursesRequest", description = "The courses of the curriculum of the university")
public record LoadCoursesRequest(
    @NotEmpty @Size(max = 1000) List<@NotNull @Valid Course> courses) {

  /**
   * @param code what the academic system reports on an approved course
   * @param description what a student reads when searching; optional
   */
  @Schema(name = "CourseToLoad")
  public record Course(
      @NotBlank @Size(max = 32) @Schema(example = "1ASI0657") String code,
      @NotBlank @Size(max = 160) @Schema(example = "Software Architecture Fundamentals") String name,
      @Size(max = 500) @Schema(nullable = true, example = "Architectural styles and quality attributes")
          String description) {}

  List<CourseToLoad> toCourses() {
    return courses.stream()
        .map(course -> new CourseToLoad(course.code(), course.name(), course.description()))
        .toList();
  }
}
