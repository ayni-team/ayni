package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.PresenceCheck;
import pe.ayni.sessions.domain.model.PresenceCheckUnavailable;
import pe.ayni.sessions.domain.model.PresenceResendRefused;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.ParticipationRepository;
import pe.ayni.sessions.infrastructure.PresenceCheckRepository;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US54, scenario 1: five minutes into a session each participant gets a six digit code.
 *
 * <p>Both participants get one, and only once the session has started, which it does when both
 * checked in: a session that never started, or was abandoned for an absence, issues nothing. Only
 * the hash is stored; the code in clear travels in {@link PresenceCodeIssued}, which notifications
 * emails once this transaction commits, so a code whose check was rolled back is never sent.
 *
 * <p>Each session is issued in a transaction of its own, so a failure in one does not hold back the
 * codes of the others. The session is locked while it is issued, so a sweep that overlaps with
 * another issues each code once; {@code uq_presence_checks_session_user} stands behind it.
 *
 * <p>It also sends one participant's code again (US54-T4), because the way a code is made, kept
 * and published is the same: the row of the participant takes a new code, and {@link
 * PresenceCodeIssued} goes out again for that person alone. The session is locked while it is
 * resent, so two requests at once replace the code once and the second finds the last resend too
 * recent.
 */
@Service
public class IssuePresenceCodesUseCase {

  private final SessionRepository sessions;
  private final ParticipationRepository participations;
  private final PresenceCheckRepository presenceChecks;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final int maxResends;
  private final Duration minResendInterval;

  IssuePresenceCodesUseCase(
      SessionRepository sessions,
      ParticipationRepository participations,
      PresenceCheckRepository presenceChecks,
      ApplicationEventPublisher events,
      Clock clock,
      @Value("${ayni.sessions.presence-resend-max:3}") int maxResends,
      @Value("${ayni.sessions.presence-resend-min-interval:PT60S}") Duration minResendInterval) {
    this.sessions = sessions;
    this.participations = participations;
    this.presenceChecks = presenceChecks;
    this.events = events;
    this.clock = clock;
    this.maxResends = maxResends;
    this.minResendInterval = minResendInterval;
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
      publish(tenantId, sessionId, participant, code, check.getExpiresAt(), now);
    }
    return true;
  }

  /**
   * Replaces the code of one participant with a new one, and publishes it for them alone.
   *
   * <p>Only a participant who joined, in a session in progress whose code was already issued and
   * not yet confirmed. The caller gets nothing back: the new code travels only by email.
   *
   * @throws NoSuchElementException when the session does not exist in the current university
   * @throws NotAParticipant when the person is neither the student nor the tutor
   * @throws PresenceCheckUnavailable when the session is not in progress, the person never joined
   *     it or no code was issued to them yet
   * @throws PresenceResendRefused when presence is confirmed, the attempts are spent, the resends
   *     are used up or the last one was too recent
   */
  @Transactional
  public void resend(UUID sessionId, UUID userId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(userId, "userId must not be null");

    String tenantId = TenantContext.require();
    Instant now = clock.instant();

    Session session =
        sessions
            .lockByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    session.roleOf(userId);
    session.requireInProgressForPresence();

    if (participations.findByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId).isEmpty()) {
      throw new PresenceCheckUnavailable("Join the session before asking for your presence code");
    }

    PresenceCheck check =
        presenceChecks
            .lockByTenantIdAndSessionIdAndUserId(tenantId, sessionId, userId)
            .orElseThrow(
                () ->
                    new PresenceCheckUnavailable(
                        "Your presence code has not been sent yet. It is sent at "
                            + session.presenceCheckAt()));

    String code = check.reissue(now, maxResends, minResendInterval);
    publish(tenantId, sessionId, userId, code, check.getExpiresAt(), now);
  }

  private void publish(
      String tenantId, UUID sessionId, UUID userId, String code, Instant expiresAt, Instant now) {
    events.publishEvent(new PresenceCodeIssued(tenantId, sessionId, userId, code, expiresAt, now));
  }
}
