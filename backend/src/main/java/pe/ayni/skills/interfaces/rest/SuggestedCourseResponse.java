package pe.ayni.skills.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import pe.ayni.skills.application.SuggestedCoursesQuery;

/** A course the tutor's academic record already clears, ready to be offered. */
@Schema(name = "SuggestedCourse", description = "A course the tutor could offer without searching for it")
public record SuggestedCourseResponse(
    CatalogItemResponse item, @Schema(example = "17.50") BigDecimal grade) {

  static SuggestedCourseResponse of(SuggestedCoursesQuery.SuggestedCourse suggestion) {
    return new SuggestedCourseResponse(CatalogItemResponse.of(suggestion.item()), suggestion.grade());
  }
}
