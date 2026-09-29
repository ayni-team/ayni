package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.PresenceCheck;
import pe.ayni.sessions.domain.model.PresenceCheckUnavailable;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.WrongPresenceCode;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.PresenceCheckRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US54, scenarios 2 and 4: a participant types in the code they were emailed.
 *
 * <p>Only a participant who joined the session can confirm: the code proves they are in the room,
 * and somebody who never entered it is not, whatever their mailbox holds.
 *
 * <p>A wrong code is refused and still counted. The transaction commits on {@link
 * WrongPresenceCode} for that reason: rolling it back would also roll back the attempt, and the
 * cap of five would never be reached. The code is locked while it is checked, so wrong codes sent
 * at once are counted one after the other.
 */
@Service
public class ConfirmPresenceUseCase {

  private final SessionRepository sessions;
  private final ParticipationRepository participations;
  private final PresenceCheckRepository presenceChecks;
  private final Clock clock;

  ConfirmPresenceUseCase(
      SessionRepository sessions,
      ParticipationRepository participations,
      PresenceCheckRepository presenceChecks,
      Clock clock) {
    this.sessions = sessions;
    this.participations = participations;
    this.presenceChecks = presenceChecks;
    this.clock = clock;
  }

  /**
   * @throws NoSuchElementException when the session does not exist in the current university
   * @throws NotAParticipant when the person is neither the student nor the tutor
   * @throws PresenceCheckUnavailable when presence cannot be confirmed now, whatever the code
   * @throws WrongPresenceCode when the code is not the one sent; the attempt is kept
   */
  @Transactional(noRollbackFor = WrongPresenceCode.class)
  public PresenceConfirmation execute(UUID sessionId, UUID userId, String code) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(code, "code must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    Session session =
        sessions
            .findByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    session.roleOf(userId);
    session.requireInProgressForPresence();

    if (participations.findByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId).isEmpty()) {
      throw new PresenceCheckUnavailable("Join the session before confirming your presence");
    }

    PresenceCheck check =
        presenceChecks
            .lockByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId)
            .orElseThrow(
                () ->
                    new PresenceCheckUnavailable(
                        "Your presence code has not been sent yet. It is sent at "
                            + session.presenceCheckAt()));

    check.confirm(code, now);
    return new PresenceConfirmation(sessionId, check.getConfirmedAt());
  }
}
