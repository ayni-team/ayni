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
import pe.ayni.sessions.domain.model.PresenceCheck;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.PresenceCheckRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US54, scenario 1: five minutes into a session each participant gets a six digit code.
 *
 * <p>Both participants get one after the session has started. Only the hash is stored; the code in
 * clear travels in {@link PresenceCodeIssued}, which notifications emails once this transaction
 * commits, so a code whose check was rolled back is never sent.
 *
 * <p>Each session is issued in a transaction of its own, so a failure in one does not hold back the
 * codes of the others. The session is locked while it is issued, so a sweep that overlaps with
 * another issues each code once; {@code uq_presence_checks_session_user} stands behind it.
 */
@Service
public class IssuePresenceCodesUseCase {

  private final SessionRepository sessions;
  private final PresenceCheckRepository presenceChecks;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  IssuePresenceCodesUseCase(
      SessionRepository sessions,
      PresenceCheckRepository presenceChecks,
      ApplicationEventPublisher events,
      Clock clock) {
    this.sessions = sessions;
    this.presenceChecks = presenceChecks;
    this.events = events;
    this.clock = clock;
  }

  /** The sessions of the current university whose codes are due now and not issued yet. */
  @Transactional(readOnly = true)
  public List<UUID> dueNow() {
    Instant now = clock.instant();
    return sessions.findDueForPresenceCheck(
        TenantContext.require(), now.minus(Session.PRESENCE_CHECK_AFTER), now);
  }

  /**
   * Issues the codes of one session, if they are due and were not issued yet.
   *
   * @return whether codes were issued
   * @throws NoSuchElementException when the session does not exist in the current university
   */
  @Transactional
  public boolean issueFor(UUID sessionId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    Session session =
        sessions
            .lockByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    if (!session.isDueForPresenceCheck(now)
        || presenceChecks.existsByTenantIdAndSessionId(tenantId, sessionId)) {
      return false;
    }

    for (UUID participant : List.of(session.getStudentId(), session.getTutorId())) {
      String code = PresenceCheck.newCode();
      PresenceCheck check =
          presenceChecks.save(
              PresenceCheck.issue(UUID.randomUUID(), tenantId, sessionId, participant, code, now));
      events.publishEvent(
          new PresenceCodeIssued(
              tenantId, sessionId, participant, code, check.getExpiresAt(), now));
    }
    return true;
  }
}
