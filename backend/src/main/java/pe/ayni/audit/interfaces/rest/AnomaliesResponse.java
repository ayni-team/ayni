package pe.ayni.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.audit.application.ListAnomaliesUseCase.AnomalyPage;

/**
 * A page of the alerts of the university, the most recently detected first.
 *
 * @param total how many alerts match in all, not only in this page
 */
@Schema(name = "AnomaliesPage", description = "A page of the alerts of anomalous usage")
public record AnomaliesResponse(List<Entry> items, int page, int size, long total) {

  /** One alert as a list shows it. */
  @Schema(name = "AnomalyEntry")
  public record Entry(
      @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID id,
      @Schema(
              allowableValues = {
                "REPEATED_PAIR",
                "UNUSUAL_FREQUENCY",
                "SHORT_DURATION",
                "FAILED_PRESENCE",
                "NO_BOARD_ACTIVITY"
              },
              example = "SHORT_DURATION")
          String kind,
      @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}, example = "MEDIUM") String severity,
      @Schema(allowableValues = {"OPEN", "DISMISSED", "CONFIRMED"}, example = "OPEN") String status,
      @Schema(
              description = "The tutor whose credits the pattern puts in question",
              example = "11111111-1111-4111-8111-111111111111")
          UUID subjectId,
      @Schema(example = "A completed session lasted 10 of the 60 minutes that were booked")
          String description,
      @Schema(example = "1") int sessionCount,
      @Schema(example = "2026-10-12T15:00:00Z") Instant detectedAt) {}

  static AnomaliesResponse of(AnomalyPage page) {
    return new AnomaliesResponse(
        page.items().stream()
            .map(
                item ->
                    new Entry(
                        item.id(),
                        item.kind(),
                        item.severity(),
                        item.status(),
                        item.subjectId(),
                        item.description(),
                        item.sessionCount(),
                        item.detectedAt()))
            .toList(),
        page.page(),
        page.size(),
        page.total());
  }
}
