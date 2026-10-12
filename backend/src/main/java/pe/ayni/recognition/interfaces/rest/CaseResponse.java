package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.recognition.application.RequestCaseQuery.Case;
import pe.ayni.recognition.application.RequestCaseQuery.ReviewedSession;
import pe.ayni.recognition.application.SessionAlerts.Alert;

/**
 * A recognition request with all its evidence, as the coordinator reviews it.
 *
 * <p>The figures and the sessions are the ones the request was submitted with and do not change while
 * it is evaluated. The alerts are what the audit found about those sessions.
 *
 * @param totalHours the hours the student presented
 * @param averageRating mean of the ratings the tutor had received, absent when there were none
 * @param reviewedAt when it was decided, absent while it waits
 * @param decisionReason why it was decided so, absent while it waits
 * @param alerts every alert of the audit about the sessions of this request, together
 */
@Schema(name = "RecognitionCase", description = "A recognition request with its evidence")
public record CaseResponse(
    @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(allowableValues = {"SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED"}, example = "SUBMITTED")
        String status,
    QueueResponse.Student student,
    @Schema(example = "20") int totalHours,
    @Schema(example = "12") int sessionsCount,
    @Schema(nullable = true, example = "4.60") BigDecimal averageRating,
    @Schema(example = "2026-10-05T09:00:00Z") Instant submittedAt,
    @Schema(nullable = true, example = "2026-10-08T15:30:00Z") Instant reviewedAt,
    @Schema(nullable = true, example = "The hours match the extracurricular credit of the programme")
        String decisionReason,
    List<Session> sessions,
    List<CaseAlert> alerts) {

  /**
   * A session that backs the request.
   *
   * @param hours the hours booked, which are the credits earned
   * @param durationMinutes how long the session lasted, from when it began to when it ended
   * @param presenceVerified always true: only sessions in which both participants confirmed their
   *     presence code back a request
   * @param stars the rating the tutor had received, absent when there was none
   */
  @Schema(name = "RecognitionCaseSession")
  public record Session(
      @Schema(example = "e0000000-0000-4000-8000-000000000001") UUID sessionId,
      @Schema(example = "2026-09-20T15:00:00Z") Instant startedAt,
      @Schema(example = "2026-09-20T17:03:00Z") Instant endedAt,
      @Schema(example = "123") long durationMinutes,
      @Schema(example = "2") int hours,
      @Schema(example = "true") boolean presenceVerified,
      @Schema(nullable = true, example = "5") Integer stars,
      List<CaseAlert> alerts) {}

  /** What the audit found about a session. */
  @Schema(name = "RecognitionCaseAlert")
  public record CaseAlert(
      @Schema(example = "e0000000-0000-4000-8000-000000000001") UUID sessionId,
      @Schema(example = "SHORT_SESSIONS") String kind,
      @Schema(example = "HIGH") String severity,
      @Schema(example = "Six sessions between the same two students ended within ten minutes") String description) {}

  static CaseResponse of(Case file) {
    List<Session> sessions = file.sessions().stream().map(CaseResponse::session).toList();
    return new CaseResponse(
        file.request().getId(),
        file.request().getStatus().name(),
        new QueueResponse.Student(file.student().id(), file.student().fullName(), file.student().studentCode()),
        file.request().getTotalHours(),
        file.request().getSessionsCount(),
        file.request().getAverageRating(),
        file.request().getSubmittedAt(),
        file.request().getReviewedAt(),
        file.request().getDecisionReason(),
        sessions,
        sessions.stream().flatMap(session -> session.alerts().stream()).toList());
  }

  private static Session session(ReviewedSession reviewed) {
    var session = reviewed.session();
    UUID id = session.getSessionId();
    return new Session(
        id,
        session.getStartedAt(),
        session.getEndedAt(),
        Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes(),
        session.getHours(),
        true,
        session.getStars(),
        reviewed.alerts().stream().map(alert -> alert(id, alert)).toList());
  }

  private static CaseAlert alert(UUID sessionId, Alert alert) {
    return new CaseAlert(sessionId, alert.kind(), alert.severity(), alert.description());
  }
}
