package pe.ayni.audit.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.ayni.audit.application.AnomalyDetail;

/**
 * One alert with what supports it.
 *
 * @param pattern the figures that made it an anomaly, as they were when it was detected
 * @param people who it involves, with the name the university has for them
 * @param sessions the concrete sessions that back it
 * @param resolution {@code null} while the alert is open
 */
@Schema(name = "Anomaly", description = "An alert of anomalous usage with the people and sessions behind it")
public record AnomalyResponse(
    @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(example = "REPEATED_PAIR") String kind,
    @Schema(example = "HIGH") String severity,
    @Schema(example = "OPEN") String status,
    @Schema(example = "The same tutor and student completed 4 sessions in the last 30 days, more than the 3 that is usual")
        String description,
    @Schema(example = "2026-10-12T15:00:00Z") Instant detectedAt,
    @Schema(example = "{\"sessions\": \"4\", \"threshold\": \"3\", \"periodDays\": \"30\"}")
        Map<String, String> pattern,
    List<Person> people,
    List<Session> sessions,
    Resolution resolution) {

  /** Somebody the alert involves. */
  @Schema(name = "AnomalyPerson")
  public record Person(
      @Schema(example = "11111111-1111-4111-8111-111111111111") UUID id,
      @Schema(allowableValues = {"TUTOR", "STUDENT"}, example = "TUTOR") String role,
      @Schema(nullable = true, example = "Bruno Salas") String fullName) {}

  /** A session that backs the alert. */
  @Schema(name = "AnomalySessionEvidence")
  public record Session(
      @Schema(example = "22222222-2222-4222-8222-222222222222") UUID id,
      @Schema(nullable = true, example = "2026-10-12T15:00:00Z") Instant scheduledStart,
      @Schema(nullable = true, example = "2026-10-12T16:00:00Z") Instant scheduledEnd,
      @Schema(nullable = true, example = "COMPLETED") String status) {}

  /** What a coordinator decided. */
  @Schema(name = "AnomalyResolution")
  public record Resolution(
      @Schema(example = "33333333-3333-4333-8333-333333333333") UUID resolvedBy,
      @Schema(example = "2026-10-13T09:00:00Z") Instant resolvedAt,
      @Schema(example = "They are study partners and the sessions are planned") String reason) {}

  static AnomalyResponse of(AnomalyDetail detail) {
    return new AnomalyResponse(
        detail.id(),
        detail.kind(),
        detail.severity(),
        detail.status(),
        detail.description(),
        detail.detectedAt(),
        detail.pattern(),
        detail.people().stream()
            .map(person -> new Person(person.id(), person.role(), person.fullName()))
            .toList(),
        detail.sessions().stream()
            .map(
                session ->
                    new Session(
                        session.id(), session.scheduledStart(), session.scheduledEnd(), session.status()))
            .toList(),
        detail.resolution() == null
            ? null
            : new Resolution(
                detail.resolution().resolvedBy(),
                detail.resolution().resolvedAt(),
                detail.resolution().reason()));
  }
}
