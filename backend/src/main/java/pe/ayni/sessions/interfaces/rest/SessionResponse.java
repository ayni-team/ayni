package pe.ayni.sessions.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.application.PresenceState;
import pe.ayni.sessions.application.SessionDetails;
import pe.ayni.sessions.domain.model.ParticipantRole;

/**
 * A session, as one of its participants sees it. The room name is only given by joining, and the
 * presence code only by email.
 */
@Schema(name = "Session")
public record SessionResponse(
    @Schema(example = "5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f") UUID id,
    @Schema(example = "d1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f") UUID bookingId,
    @Schema(example = "11111111-1111-4111-8111-111111111111") UUID studentId,
    @Schema(example = "22222222-2222-4222-8222-222222222222") UUID tutorId,
    @Schema(description = "The course the session is about",
        example = "b0000000-0000-4000-8000-000000000102")
        UUID catalogItemId,
    @Schema(description = "Which of the two the reader is", example = "TUTOR") ParticipantRole role,
    @Schema(example = "SCHEDULED") SessionStatus status,
    @Schema(description = "UTC", example = "2026-09-30T20:00:00Z") Instant scheduledStart,
    @Schema(description = "UTC", example = "2026-09-30T21:00:00Z") Instant scheduledEnd,
    @Schema(description = "First moment the room can be joined, UTC",
        example = "2026-09-30T19:45:00Z")
        Instant joinOpensAt,
    @Schema(description = "When the first participant joined; null until then", nullable = true,
        example = "2026-09-30T19:52:10Z")
        Instant startedAt,
    @Schema(description = "What the student needs help with, written when booking",
        example = "Normal forms before Friday's exam")
        String needDescription,
    @Schema(description = "When the presence codes are emailed: five minutes after the scheduled "
        + "start, UTC", example = "2026-09-30T20:05:00Z")
        Instant presenceCheckAt,
    @Schema(description = "The reader's own presence code, without the code; null until it is sent",
        nullable = true)
        Presence presence,
    @Schema(description = "When the reader confirmed the end; null if they have not",
        nullable = true, example = "2026-09-30T20:58:00Z")
        Instant endConfirmedAt,
    @Schema(description = "When the session closed; null while it has not", nullable = true,
        example = "2026-09-30T20:59:10Z")
        Instant endedAt,
    @Schema(description = "When the session closes on its own if the participants do not close "
        + "it: fifteen minutes after the booked hour, UTC", example = "2026-09-30T21:15:00Z")
        Instant closesAt) {

  /** The reader's presence code as the screen needs it. The code itself only travels by email. */
  @Schema(name = "SessionPresence")
  public record Presence(
      @Schema(description = "UTC", example = "2026-09-30T20:05:00Z") Instant issuedAt,
      @Schema(description = "Last moment the code can be typed in, UTC",
          example = "2026-09-30T20:20:00Z")
          Instant expiresAt,
      @Schema(description = "When presence was confirmed; null until then", nullable = true,
          example = "2026-09-30T20:06:30Z")
          Instant confirmedAt,
      @Schema(description = "Wrong codes that can still be typed before the code is useless",
          example = "5")
          int attemptsLeft) {

    static Presence of(PresenceState state) {
      return state == null
          ? null
          : new Presence(
              state.issuedAt(), state.expiresAt(), state.confirmedAt(), state.attemptsLeft());
    }
  }

  static SessionResponse of(SessionDetails session) {
    return new SessionResponse(
        session.id(),
        session.bookingId(),
        session.studentId(),
        session.tutorId(),
        session.catalogItemId(),
        session.role(),
        session.status(),
        session.scheduledStart(),
        session.scheduledEnd(),
        session.joinOpensAt(),
        session.startedAt(),
        session.needDescription(),
        session.presenceCheckAt(),
        Presence.of(session.presence()),
        session.endConfirmedAt(),
        session.endedAt(),
        session.closesAt());
  }
}
