package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.Participation;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.SessionNotOpen;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US11: a participant confirms the session is over, and the second confirmation closes it.
 *
 * <p>The story asks for both to confirm the end before the session is completed. One
 * confirmation is recorded and the session waits for the other; if it never comes, the session
 * closes on its own {@link Session#CLOSES_AFTER_END} after the booked hour, and the record shows who
 * confirmed. Only a participant who joined can confirm, since only they were in the session.
 *
 * <p>The session is locked while it is ended, so the two confirming at once close it once: without
 * the lock each could see the other's confirmation missing, or both see both and close it twice.
 */
@Service
public class EndSessionUseCase {

  private final SessionRepository sessions;
  private final ParticipationRepository participations;
  private final SessionCloser closer;
  private final Clock clock;

  EndSessionUseCase(
      SessionRepository sessions,
      ParticipationRepository participations,
      SessionCloser closer,
      Clock clock) {
    this.sessions = sessions;
    this.participations = participations;
    this.closer = closer;
    this.clock = clock;
  }

  /**
   * @throws NoSuchElementException when the session does not exist in the current university
   * @throws NotAParticipant when the person is neither the student nor the tutor
   * @throws SessionNotOpen when the session is not in progress, or the person never joined it
   */
  @Transactional
  public EndedSession execute(UUID sessionId, UUID userId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    Session session =
        sessions
            .lockByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    session.roleOf(userId);
    session.requireInProgressToEnd();

    List<Participation> present = participations.findByTenantIdAndSessionId(tenantId, sessionId);
    Participation mine =
        present.stream()
            .filter(participation -> participation.getUserId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new SessionNotOpen("Join the session before ending it"));
    mine.confirmEnd(now);

    boolean bothConfirmed =
        present.size() == 2 && present.stream().allMatch(Participation::hasConfirmedEnd);
    if (bothConfirmed) {
      closer.close(session, now);
    }

    return new EndedSession(
        session.getId(),
        session.getStatus(),
        mine.getEndConfirmedAt(),
        session.getEndedAt(),
        session.closesAt());
  }
}
