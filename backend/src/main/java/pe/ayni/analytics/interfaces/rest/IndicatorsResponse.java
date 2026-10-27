package pe.ayni.analytics.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import pe.ayni.analytics.domain.model.UsageFigures;
import pe.ayni.analytics.domain.model.UsageIndicators;

/**
 * The usage of the university in a period: the totals, the series with one entry per day and the
 * courses sought and not covered.
 */
@Schema(name = "UsageIndicators", description = "Figures only: never a name or an identifier of a person")
public record IndicatorsResponse(
    @Schema(description = "First day, included", example = "2026-10-01") LocalDate from,
    @Schema(description = "Last day, included", example = "2026-10-31") LocalDate to,
    Figures totals,
    List<Day> days,
    @Schema(
            description =
                "The courses most searched without finding an hour inside the window asked for in"
                    + " the period, the most searched first and at most 20. Identifiers only: the"
                    + " names are in GET /api/v1/catalog")
        List<UncoveredCourse> uncoveredDemand) {

  /** The five figures, for a day or for the whole period. */
  @Schema(name = "UsageFigures")
  public record Figures(
      @Schema(description = "Tutoring sessions completed") long sessionsCompleted,
      @Schema(description = "Hours exchanged: one credit is one hour") long hoursExchanged,
      @Schema(
              description =
                  "Distinct students who took part. In the totals they are distinct over the"
                      + " whole period, not the sum of the days")
          long activeStudents,
      @Schema(description = "Students who entered for the first time") long newStudents,
      @Schema(description = "Recognitions the university granted") long recognitionsGranted) {

    static Figures of(UsageFigures figures) {
      return new Figures(
          figures.sessionsCompleted(),
          figures.hoursExchanged(),
          figures.activeStudents(),
          figures.newStudents(),
          figures.recognitionsGranted());
    }
  }

  /** One day of the series, by the time zone of the university. */
  @Schema(name = "UsageDay")
  public record Day(LocalDate day, Figures figures) {}

  /** A course students looked for in the period and found no hour. */
  @Schema(name = "UncoveredCourse")
  public record UncoveredCourse(
      @Schema(
              description = "The course (catalogue item)",
              example = "b0000000-0000-4000-8000-000000000102")
          UUID catalogItemId,
      @Schema(description = "Searches of it that found no hour in the period", example = "14")
          long searches) {}

  static IndicatorsResponse of(UsageIndicators indicators) {
    return new IndicatorsResponse(
        indicators.period().from(),
        indicators.period().to(),
        Figures.of(indicators.totals()),
        indicators.days().stream().map(d -> new Day(d.day(), Figures.of(d.figures()))).toList(),
        indicators.uncoveredDemand().stream()
            .map(course -> new UncoveredCourse(course.catalogItemId(), course.searches()))
            .toList());
  }
}
