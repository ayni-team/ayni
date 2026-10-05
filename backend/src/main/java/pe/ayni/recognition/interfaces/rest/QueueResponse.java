package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.recognition.application.RequestQueueQuery.QueuePage;
import pe.ayni.recognition.application.RequestQueueQuery.QueuedRequest;

/**
 * A page of the recognition requests of the university, the one that waited longest first.
 *
 * @param items the requests of this page
 * @param page from zero
 * @param size what was asked for, at most 100
 * @param total how many requests there are in all, not only in this page
 */
@Schema(name = "RecognitionQueue", description = "The recognition requests of the university")
public record QueueResponse(List<Item> items, int page, int size, long total) {

  /**
   * One request.
   *
   * @param totalHours the hours the student presented, as they were when they submitted
   * @param submittedAt since when the request waits for a decision
   */
  @Schema(name = "RecognitionQueueItem")
  public record Item(
      @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID id,
      Student student,
      @Schema(allowableValues = {"SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED"}, example = "SUBMITTED")
          String status,
      @Schema(example = "20") int totalHours,
      @Schema(example = "12") int sessionsCount,
      @Schema(nullable = true, example = "4.60") BigDecimal averageRating,
      @Schema(example = "2026-10-05T09:00:00Z") Instant submittedAt) {}

  /** Who asked. */
  @Schema(name = "RecognitionStudent")
  public record Student(
      @Schema(example = "11111111-1111-4111-8111-111111111111") UUID id,
      @Schema(example = "Ana Torres") String name,
      @Schema(nullable = true, example = "U202310949") String studentCode) {}

  static QueueResponse of(QueuePage page) {
    return new QueueResponse(
        page.items().stream().map(QueueResponse::item).toList(), page.page(), page.size(), page.total());
  }

  private static Item item(QueuedRequest queued) {
    return new Item(
        queued.request().getId(),
        new Student(queued.student().id(), queued.student().fullName(), queued.student().studentCode()),
        queued.request().getStatus().name(),
        queued.request().getTotalHours(),
        queued.request().getSessionsCount(),
        queued.request().getAverageRating(),
        queued.request().getSubmittedAt());
  }
}
