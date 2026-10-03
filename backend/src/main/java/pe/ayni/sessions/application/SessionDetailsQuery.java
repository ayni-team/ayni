package pe.ayni.sessions.application;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingView;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.Participation;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.PresenceCheckRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * A session, for one of its two participants and nobody else.
 *
 * <p>The need description stays in booking, which owns it, and is read through {@link BookingApi}
 * rather than copied into sessions: the session keeps only the booking's id, as the booking module
 * already expected.
 *
 * <p>The room name is not part of the answer. It is what lets anyone into the call, so it is only
 * handed over by joining, inside the window the room is open. Nor is the presence code, which only
 * travels by email: the answer says whether it was sent, until when it can be typed in and whether
 * it was, for the reader alone. The first check-in times are visible to both participants.
 */
@Service
public class SessionDetailsQuery {

  private final SessionRepository sessions;
  private final ParticipationRepository participations;
  private final PresenceCheckRepository presenceChecks;
  private final BookingApi booking;

  SessionDetailsQuery(
      SessionRepository sessions,
      ParticipationRepository participations,
      PresenceCheckRepository presenceChecks,
      BookingApi booking) {
    this.sessions = sessions;
    this.participations = participations;
    this.presenceChecks = presenceChecks;
    this.booking = booking;
  }

  /**
   * @throws NoSuchElementException when the session does not exist in the current university
   * @throws NotAParticipant when the reader is neither the student nor the tutor
   */
  @Transactional(readOnly = true)
  public SessionDetails execute(UUID sessionId, UUID userId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    String tenantId = TenantContext.require();
    Session session =
        sessions
            .findByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    // Refused before anything else is read, booking included.
    ParticipantRole role = session.roleOf(userId);
    BookingView booked = booking.requireBooking(session.getBookingId());
    PresenceState presence =
        presenceChecks
            .findByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId)
            .map(
                check ->
                    new PresenceState(
                        check.getIssuedAt(),
                        check.getExpiresAt(),
                        check.getConfirmedAt(),
                        check.attemptsLeft()))
            .orElse(null);
    Instant endConfirmedAt =
        participations
            .findByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId)
            .map(Participation::getEndConfirmedAt)
            .orElse(null);
    List<Participation> arrivals =
        participations.findByTenantIdAndSessionId(tenantId, sessionId);
    Instant studentJoinedAt = joinedAt(arrivals, ParticipantRole.STUDENT);
    Instant tutorJoinedAt = joinedAt(arrivals, ParticipantRole.TUTOR);
    List<UUID> absentParticipantIds =
        session.getStatus() == SessionStatus.ABANDONED
            ? absentParticipantIds(session, arrivals)
            : List.of();

    return new SessionDetails(
        session.getId(),
        session.getBookingId(),
        session.getStudentId(),
        session.getTutorId(),
        booked.catalogItemId(),
        role,
        session.getStatus(),
        session.getScheduledStart(),
        session.getScheduledEnd(),
        session.joinOpensAt(),
        session.getStartedAt(),
        booked.needDescription(),
        session.presenceCheckAt(),
        presence,
        studentJoinedAt,
        tutorJoinedAt,
        absentParticipantIds,
        endConfirmedAt,
        session.getEndedAt(),
        session.closesAt());
  }

  private static Instant joinedAt(List<Participation> arrivals, ParticipantRole role) {
    return arrivals.stream()
        .filter(participation -> participation.getRole() == role)
        .map(Participation::getJoinedAt)
        .findFirst()
        .orElse(null);
  }

  private static List<UUID> absentParticipantIds(Session session, List<Participation> arrivals) {
    Instant deadline = session.tutorNoShowAt();
    return List.of(session.getStudentId(), session.getTutorId()).stream()
        .filter(
            userId ->
                arrivals.stream()
                    .filter(participation -> participation.getUserId().equals(userId))
                    .noneMatch(participation -> participation.hasCheckedInBy(deadline)))
        .toList();
  }
}
