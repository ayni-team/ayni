package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * announces the start when this join is the one that started it.
 *
 * <p>The session is locked rather than read, because the two participants usually arrive within
 * seconds of each other: without the lock both could find it not started, both start it and {@link
 * SessionStarted} would be published twice.
 */
@Service
public class JoinSessionUseCase {

  private final SessionRepository sessions;
  private final ParticipationRepository participations;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  JoinSessionUseCase(
      SessionRepository sessions,
      ParticipationRepository participations,
      ApplicationEventPublisher events,
      Clock clock) {
    this.sessions = sessions;
    this.participations = participations;
    this.events = events;
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

    boolean startedNow = session.join(userId, now);
    ParticipantRole role = session.roleOf(userId);

    if (participations.findByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId).isEmpty()) {
      participations.save(
          Participation.arrived(UUID.randomUUID(), tenantId, sessionId, userId, role, now));
    }

    if (startedNow) {
      events.publishEvent(new SessionStarted(tenantId, sessionId, session.getBookingId(), now));
    }

    return new JoinedSession(
        session.getId(),
        role,
        session.getRoomName(),
        session.getStatus(),
        session.getStartedAt(),
        session.getScheduledStart(),
        session.getScheduledEnd());
  }
}
