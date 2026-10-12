package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import pe.ayni.skills.domain.model.CatalogItem;

/** A course of the curriculum of the university. */
@Schema(name = "AcademicCourse", description = "A course of the curriculum of the university")
public record AcademicCourseResponse(
    @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID id,
    @Schema(example = "1ASI0657", description = "The code the academic system reports") String code,
    @Schema(example = "Software Architecture Fundamentals") String name,
    @Schema(nullable = true, example = "Architectural styles and quality attributes") String description,
    @Schema(allowableValues = {"ACTIVE", "RETIRED"}, example = "ACTIVE") String status) {

  static AcademicCourseResponse of(CatalogItem course) {
    return new AcademicCourseResponse(
        course.getId(),
        course.getCourseCode(),
        course.getName(),
        course.getDescription(),
        course.getStatus().name());
  }
}
