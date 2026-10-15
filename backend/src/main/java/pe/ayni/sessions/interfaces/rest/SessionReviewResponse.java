package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.application.SessionReview;

/** A past session as one of its participants reviews it (US12). */
@Schema(name = "SessionReview")
public record SessionReviewResponse(
    UUID sessionId,
    @Schema(example = "COMPLETED") SessionStatus status,
    @Schema(description = "What happened to it, so a session that did not take place shows why",
        example = "The booking was cancelled before the session started")
        String statusNote,
    @Schema(description = "UTC") Instant scheduledStart,
    @Schema(description = "UTC") Instant scheduledEnd,
    @Schema(description = "When both participants were in, UTC; null if it never started",
        nullable = true)
        Instant startedAt,
    @Schema(description = "When it closed, was abandoned or was cancelled, UTC; null while open",
        nullable = true)
        Instant endedAt,
    @Schema(description = "The minutes booked", example = "60") long bookedMinutes,
    @Schema(description = "The minutes from both being in to the close; null if it never took "
        + "place", nullable = true, example = "52")
        Long realDurationMinutes,
    @Schema(description = "What the student needed help with") String needDescription,
    @Schema(description = "The support material prepared from the need; null when there was none",
        nullable = true)
        SessionResponse.Material supportMaterial,
    @Schema(description = "The whiteboard as it was left; null when the session never started",
        nullable = true)
        WhiteboardResponse whiteboard,
    @Schema(description = "Who did not come, for a session abandoned for that reason")
        List<UUID> absentParticipantIds) {

  static SessionReviewResponse of(SessionReview review) {
    return new SessionReviewResponse(
        review.sessionId(),
        review.status(),
        review.statusNote(),
        review.scheduledStart(),
        review.scheduledEnd(),
        review.startedAt(),
        review.endedAt(),
        review.bookedMinutes(),
        review.realDurationMinutes(),
        review.needDescription(),
        SessionResponse.Material.of(review.supportMaterial()),
        review.whiteboard() == null ? null : WhiteboardResponse.of(review.whiteboard()),
        review.absentParticipantIds());
  }
}
