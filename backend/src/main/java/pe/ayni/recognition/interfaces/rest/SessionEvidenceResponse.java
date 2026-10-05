package pe.ayni.recognition.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.recognition.application.SessionEvidenceQuery.Evidence;
import pe.ayni.recognition.application.SessionEvidenceQuery.Participant;
import pe.ayni.skills.CatalogScope;

/**
 * The evidence of one tutoring session that backs a recognition request.
 *
 * @param attendance the register of both participants: who they are and whether their presence was
 *     verified
 * @param tutorStars the rating the tutor received for this session when the request was submitted,
 *     absent when there was none
 */
@Schema(name = "RecognitionSessionEvidence", description = "The evidence of a session behind a request")
public record SessionEvidenceResponse(
    @Schema(example = "e0000000-0000-4000-8000-000000000001") UUID sessionId,
    @Schema(example = "d0000000-0000-4000-8000-000000000001") UUID requestId,
    Taught taught,
    @Schema(example = "2026-09-20T15:00:00Z") Instant scheduledStart,
    @Schema(example = "2026-09-20T17:00:00Z") Instant scheduledEnd,
    @Schema(example = "2026-09-20T15:02:00Z") Instant startedAt,
    @Schema(example = "2026-09-20T17:05:00Z") Instant endedAt,
    @Schema(example = "123") long durationMinutes,
    @Schema(example = "2") int hours,
    List<Attendee> attendance,
    @Schema(nullable = true, example = "5") Integer tutorStars) {

  /** What was taught. */
  @Schema(name = "RecognitionTaught")
  public record Taught(
      @Schema(example = "b0000000-0000-4000-8000-000000000101") UUID catalogItemId,
      @Schema(example = "Databases") String name,
      @Schema(allowableValues = {"COURSE", "TOOL"}, example = "COURSE") String kind,
      @Schema(nullable = true, example = "1ASI0616") String courseCode) {}

  /** One participant in the register of attendance. */
  @Schema(name = "RecognitionAttendee")
  public record Attendee(
      @Schema(allowableValues = {"TUTOR", "STUDENT"}, example = "TUTOR") String role,
      @Schema(example = "11111111-1111-4111-8111-111111111111") UUID userId,
      @Schema(example = "Ana Torres") String name,
      @Schema(example = "true") boolean presenceVerified) {}

  static SessionEvidenceResponse of(Evidence evidence) {
    var session = evidence.session();
    var taught = evidence.taught();
    return new SessionEvidenceResponse(
        session.getSessionId(),
        evidence.request().getId(),
        new Taught(
            taught.id(),
            taught.name(),
            taught.scope() == CatalogScope.UNIVERSITY ? "COURSE" : "TOOL",
            taught.courseCode()),
        evidence.view().scheduledStart(),
        evidence.view().scheduledEnd(),
        session.getStartedAt(),
        session.getEndedAt(),
        Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes(),
        session.getHours(),
        List.of(attendee("TUTOR", evidence.tutor()), attendee("STUDENT", evidence.student())),
        session.getStars());
  }

  private static Attendee attendee(String role, Participant participant) {
    return new Attendee(role, participant.user().id(), participant.user().fullName(), participant.presenceVerified());
  }
}
