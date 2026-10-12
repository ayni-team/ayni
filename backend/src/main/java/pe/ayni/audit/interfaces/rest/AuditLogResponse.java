package pe.ayni.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.ayni.audit.application.ConsultActivityLogUseCase.LogPage;

/**
 * A page of the activity log of the university, the most recent first.
 *
 * @param items the entries of this page
 * @param page from zero
 * @param size what was asked for, at most 100
 * @param total how many entries match in all, not only in this page
 */
@Schema(name = "AuditLog", description = "A page of the activity log of the university")
public record AuditLogResponse(List<Entry> items, int page, int size, long total) {

  /**
   * One thing that happened.
   *
   * @param actorId {@code null} when it was the system, or the event does not say who
   * @param details what describes the fact: the other people involved, the amount, the outcome
   */
  @Schema(name = "AuditLogEntry")
  public record Entry(
      @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID id,
      @Schema(example = "2026-10-12T15:00:00Z") Instant occurredAt,
      @Schema(nullable = true, example = "11111111-1111-4111-8111-111111111111") UUID actorId,
      @Schema(
              allowableValues = {"STUDENT", "TUTOR", "COORDINATOR", "SYSTEM", "UNKNOWN"},
              example = "COORDINATOR")
          String actorRole,
      @Schema(example = "RECOGNITION_APPROVED") String action,
      @Schema(example = "RECOGNITION_REQUEST") String resourceType,
      @Schema(nullable = true, example = "22222222-2222-4222-8222-222222222222") UUID resourceId,
      @Schema(example = "{\"studentId\": \"11111111-1111-4111-8111-111111111111\"}")
          Map<String, String> details) {}

  static AuditLogResponse of(LogPage page) {
    return new AuditLogResponse(
        page.items().stream()
            .map(
                item ->
                    new Entry(
                        item.id(),
                        item.occurredAt(),
                        item.actorId(),
                        item.actorRole(),
                        item.action(),
                        item.resourceType(),
                        item.resourceId(),
                        item.details()))
            .toList(),
        page.page(),
        page.size(),
        page.total());
  }
}
