package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.recognition.domain.model.RecognitionRequest;
import pe.ayni.recognition.domain.model.RequestedSession;

/**
 * A request to the university, with the figures it was submitted with and the sessions that back it.
 *
 * @param status SUBMITTED while nobody decided, then APPROVED or REJECTED
 * @param averageRating mean of the ratings the tutor had received for these sessions, absent when
 *     there were none
 * @param reviewedAt when the university decided, absent while it has not
 * @param decisionReason why it decided so, absent while it has not
 */
@Schema(name = "RecognitionRequest", description = "A request for the university to recognise the hours taught")
public record RequestResponse(
    @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID id,
    @Schema(allowableValues = {"SUBMITTED", "UNDER_REVIEW", "APPROVED", "REJECTED"}, example = "SUBMITTED")
        String status,
    @Schema(example = "20") int totalHours,
    @Schema(example = "12") int sessionsCount,
    @Schema(nullable = true, example = "4.60") BigDecimal averageRating,
    @Schema(example = "2026-10-05T09:00:00Z") Instant submittedAt,
    @Schema(nullable = true, example = "2026-10-08T15:30:00Z") Instant reviewedAt,
    @Schema(nullable = true, example = "The hours match the extracurricular credit of the programme")
        String decisionReason,
    List<Session> sessions) {

  /** A session that backs the request, as it was when the request was submitted. */
  @Schema(name = "RecognitionRequestSession")
  public record Session(
      @Schema(example = "e0000000-0000-4000-8000-000000000001") UUID sessionId,
      @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID catalogItemId,
      @Schema(example = "2") int hours,
      @Schema(example = "2026-09-20T15:00:00Z") Instant startedAt,
      @Schema(example = "2026-09-20T17:00:00Z") Instant endedAt,
      @Schema(nullable = true, example = "5") Integer stars) {

    static Session of(RequestedSession session) {
      return new Session(
          session.getSessionId(),
          session.getCatalogItemId(),
          session.getHours(),
          session.getStartedAt(),
          session.getEndedAt(),
          session.getStars());
    }
  }

  static RequestResponse of(RecognitionRequest request, List<RequestedSession> sessions) {
    return new RequestResponse(
        request.getId(),
        request.getStatus().name(),
        request.getTotalHours(),
        request.getSessionsCount(),
        request.getAverageRating(),
        request.getSubmittedAt(),
        request.getReviewedAt(),
        request.getDecisionReason(),
        sessions.stream().map(Session::of).toList());
  }
}
