package pe.ayni.analytics.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import pe.ayni.analytics.domain.model.UsageFigures;
import pe.ayni.analytics.domain.model.UsageIndicators;

/** The usage of the university in a period: the totals and the series, one entry per day. */
@Schema(name = "UsageIndicators", description = "Figures only: never a name or an identifier")
public record IndicatorsResponse(
    @Schema(description = "First day, included", example = "2026-10-01") LocalDate from,
    @Schema(description = "Last day, included", example = "2026-10-31") LocalDate to,
    Figures totals,
    List<Day> days) {

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

  static IndicatorsResponse of(UsageIndicators indicators) {
    return new IndicatorsResponse(
        indicators.period().from(),
        indicators.period().to(),
        Figures.of(indicators.totals()),
        indicators.days().stream().map(d -> new Day(d.day(), Figures.of(d.figures()))).toList());
  }
}
