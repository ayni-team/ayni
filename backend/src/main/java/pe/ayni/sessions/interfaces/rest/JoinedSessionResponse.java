package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.application.JoinedSession;
import pe.ayni.sessions.application.VideoRoomPasses;
import pe.ayni.sessions.domain.model.ParticipantRole;

/** A participant let into the room. */
@Schema(name = "JoinedSession")
public record JoinedSessionResponse(
    @Schema(example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f") UUID sessionId,
    @Schema(description = "The course the session is about, with its name")
        SessionResponse.CatalogItem catalogItem,
    @Schema(example = "STUDENT") ParticipantRole role,
    @Schema(description = "The other person of the session: the tutor when the one who joined is "
        + "the student, the student when it is the tutor")
        SessionResponse.Counterpart counterpart,
    @Schema(description = "Room of the video call. Unguessable; keep it private",
        example = "ayni-3f9c2b7e1d4a4c0e9b8f6a2d5c7e1b3a")
        String roomName,
    @Schema(description = "SCHEDULED until both participants checked in; then IN_PROGRESS",
        example = "IN_PROGRESS")
        SessionStatus status,
    @Schema(description = "When both participants had checked in and the session began, UTC; "
        + "null until then", nullable = true, example = "2026-09-30T19:52:10Z")
        Instant startedAt,
    @Schema(example = "2026-09-30T20:00:00Z") Instant scheduledStart,
    @Schema(example = "2026-09-30T21:00:00Z") Instant scheduledEnd,
    @Schema(description = "When the student first arrived, UTC; null if they have not",
        nullable = true, example = "2026-09-30T19:50:03Z")
        Instant studentJoinedAt,
    @Schema(description = "When the tutor first arrived, UTC; null if they have not",
        nullable = true, example = "2026-09-30T19:52:10Z")
        Instant tutorJoinedAt,
    @Schema(description = "The pass to the video call of this session, for the one who joined; "
        + "null when no video service is configured", nullable = true)
        Video video) {

  /** What the client opens the video call with (US08). */
  @Schema(name = "SessionVideoPass")
  public record Video(
      @Schema(description = "The room in the video service; open it with the token",
          example = "http://localhost:8000/ayni-3f9c2b7e1d4a4c0e9b8f6a2d5c7e1b3a")
          String url,
      @Schema(description = "Signed token valid for this room only and a few minutes. Keep it "
          + "private: whoever holds it can enter the call until it expires")
          String token,
      @Schema(description = "When it stops letting anybody in, UTC; join again for a new one",
          example = "2026-09-30T19:57:10Z")
          Instant expiresAt) {

    static Video of(VideoRoomPasses.VideoPass pass) {
      return pass == null ? null : new Video(pass.url(), pass.token(), pass.expiresAt());
    }
  }

  static JoinedSessionResponse of(JoinedSession joined) {
    return new JoinedSessionResponse(
        joined.sessionId(),
        SessionResponse.CatalogItem.of(joined.catalogItem()),
        joined.role(),
        SessionResponse.Counterpart.of(joined.counterpart()),
        joined.roomName(),
        joined.status(),
        joined.startedAt(),
        joined.scheduledStart(),
        joined.scheduledEnd(),
        joined.studentJoinedAt(),
        joined.tutorJoinedAt(),
        Video.of(joined.video()));
  }
}
