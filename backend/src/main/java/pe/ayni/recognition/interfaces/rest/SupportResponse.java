package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.recognition.application.SupportingSessionsQuery.Support;
import pe.ayni.recognition.application.SupportingSessionsQuery.SupportingSession;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.CatalogScope;

/**
 * The sessions that back a recognition request.
 *
 * @param totalHours the hours the student presented
 * @param listedHours the hours of the sessions listed: always the same as {@code totalHours}, because
 *     both were copied when the request was submitted
 */
@Schema(name = "RecognitionSupport", description = "The tutoring sessions behind a recognition request")
public record SupportResponse(
    @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID requestId,
    @Schema(example = "20") int totalHours,
    @Schema(example = "20") int listedHours,
    List<Session> sessions) {

  /**
   * One session.
   *
   * @param hours the hours booked, which are the credits earned
   * @param durationMinutes how long the session lasted
   * @param taughtKind COURSE for a course of the university, TOOL for a global tool
   */
  @Schema(name = "RecognitionSupportSession")
  public record Session(
      @Schema(example = "e0000000-0000-4000-8000-000000000001") UUID sessionId,
      @Schema(example = "2026-09-20T15:00:00Z") Instant startedAt,
      @Schema(example = "2026-09-20T17:03:00Z") Instant endedAt,
      @Schema(example = "123") long durationMinutes,
      @Schema(example = "2") int hours,
      @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID catalogItemId,
      @Schema(example = "Databases") String taught,
      @Schema(allowableValues = {"COURSE", "TOOL"}, example = "COURSE") String taughtKind,
      @Schema(nullable = true, example = "1ASI0616") String courseCode) {}

  static SupportResponse of(Support support) {
    List<Session> sessions = support.sessions().stream().map(SupportResponse::session).toList();
    return new SupportResponse(
        support.request().getId(),
        support.request().getTotalHours(),
        sessions.stream().mapToInt(Session::hours).sum(),
        sessions);
  }

  private static Session session(SupportingSession supporting) {
    var session = supporting.session();
    CatalogItemView taught = supporting.taught();
    return new Session(
        session.getSessionId(),
        session.getStartedAt(),
        session.getEndedAt(),
        Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes(),
        session.getHours(),
        taught.id(),
        taught.name(),
        taught.scope() == CatalogScope.UNIVERSITY ? "COURSE" : "TOOL",
        taught.courseCode());
  }
}
