package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US11, scenario 3: a session that only one participant, or nobody, ended closes on its own {@link
 * Session#CLOSES_AFTER_END} after the booked hour.
 *
 * <p>It closes exactly as if both had confirmed: the outcome is the presence check's, and who
 * confirmed the end stays recorded on their participation. Each session is closed in a
 * transaction of its own, locked, so a participant ending it at the same moment closes it once
 * between the two.
 */
@Service
public class CloseOverdueSessionsUseCase {

  private final SessionRepository sessions;
  private final SessionCloser closer;
  private final Clock clock;

  CloseOverdueSessionsUseCase(SessionRepository sessions, SessionCloser closer, Clock clock) {
    this.sessions = sessions;
    this.closer = closer;
    this.clock = clock;
  }

  /** The sessions of the current university that should have closed by now. */
  @Transactional(readOnly = true)
  public List<UUID> dueNow() {
    return sessions.findDueToClose(
        TenantContext.require(), clock.instant().minus(Session.CLOSES_AFTER_END));
  }

  /**
   * Closes one session, if it is still in progress and overdue.
   *
   * @return whether it was closed now
   * @throws NoSuchElementException when the session does not exist in the current university
   */
  @Transactional
  public boolean closeFor(UUID sessionId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");

    Instant now = clock.instant();
    Session session =
        sessions
            .lockByTenantIdAndId(TenantContext.require(), sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    if (!session.isDueToClose(now)) {
      return false;
    }
    closer.close(session, now);
    return true;
  }
}
