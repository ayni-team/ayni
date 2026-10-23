package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.booking.BookingApi;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.ParticipantRole;
import pe.ayni.sessions.domain.model.Participation;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.SessionNotOpen;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.events.SessionStarted;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US08: the student and the tutor meet in the room.
 *
 * <p>Whether the room is open, and whether this person belongs in it, is the session's to decide.
 * This use case locks the session while it does, records the participant's first arrival, and
 * starts the session, announcing it, when this arrival is the one that completes the check-in of
 * both participants before the ten-minute deadline (US09). Entering the room alone starts nothing.
 *
 * <p>The session is locked rather than read, because the two participants usually arrive within
 * seconds of each other: without the lock each would record their arrival without seeing the
 * other's, neither would find both present and the session would never start, or both would start
 * it and {@link SessionStarted} would be published twice.
 */
@Service
public class JoinSessionUseCase {

  private final SessionRepository sessions;
  private final ParticipationRepository participations;
  private final ApplicationEventPublisher events;
  private final VideoRoomPasses videoPasses;
  private final BookingApi booking;
  private final SessionParties parties;
  private final Clock clock;

  JoinSessionUseCase(
      SessionRepository sessions,
      ParticipationRepository participations,
      ApplicationEventPublisher events,
      VideoRoomPasses videoPasses,
      BookingApi booking,
      SessionParties parties,
      Clock clock) {
    this.sessions = sessions;
    this.participations = participations;
    this.events = events;
    this.videoPasses = videoPasses;
    this.booking = booking;
    this.parties = parties;
    this.clock = clock;
  }

  /**
   * @throws NoSuchElementException when the session does not exist in the current university
   * @throws NotAParticipant when the person is neither the student nor the tutor
   * @throws SessionNotOpen when the room is not open
   */
  @Transactional
  public JoinedSession execute(UUID sessionId, UUID userId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    Session session =
        sessions
            .lockByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));

    session.join(userId, now);
    ParticipantRole role = session.roleOf(userId);

    if (participations.findByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId).isEmpty()) {
      participations.save(
          Participation.arrived(UUID.randomUUID(), tenantId, sessionId, userId, role, now));
    }

    List<Participation> arrivals = participations.findByTenantIdAndSessionId(tenantId, sessionId);
    if (session.startIfBothCheckedIn(arrivals, now)) {
      events.publishEvent(new SessionStarted(tenantId, sessionId, session.getBookingId(), now));
    }

    return new JoinedSession(
        session.getId(),
        role,
        parties.courseOf(booking.requireBooking(session.getBookingId()).catalogItemId()),
        parties.counterpartOf(session, role),
        session.getRoomName(),
        session.getStatus(),
        session.getStartedAt(),
        session.getScheduledStart(),
        session.getScheduledEnd(),
        arrivalOf(arrivals, session.getStudentId()),
        arrivalOf(arrivals, session.getTutorId()),
        // A new pass every time somebody joins: it opens this room only, for a few minutes (US08).
        videoPasses
            .issue(session.getRoomName(), userId, role, now, session.getScheduledEnd())
            .orElse(null));
  }

  private static Instant arrivalOf(List<Participation> arrivals, UUID userId) {
    return arrivals.stream()
        .filter(arrival -> arrival.getUserId().equals(userId))
        .map(Participation::getJoinedAt)
        .findFirst()
        .orElse(null);
  }
}
