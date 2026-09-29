package pe.ayni.sessions.application;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionView;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * What the other modules see of sessions.
 *
 * <p>The interface was published before anything implemented it, so the first module to inject it
 * would have stopped the application from starting. Recognition is that module: a request is backed
 * by the sessions a tutor completed.
 *
 * <p>Only {@link SessionStatus#COMPLETED} counts as completed. An {@code UNVERIFIED} session took
 * place but failed the presence check, and the backend guide is explicit that it earns the tutor
 * nothing, so it backs no recognition either. The hours are the hours that were booked, one credit
 * each, not the minutes the call lasted: that is what the tutor was paid for.
 */
@Service
public class SessionsService implements SessionsApi {

  private final SessionRepository sessions;

  SessionsService(SessionRepository sessions) {
    this.sessions = sessions;
  }

  @Override
  @Transactional(readOnly = true)
  public SessionView requireSession(UUID sessionId) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");

    Session session =
        sessions
            .findByTenantIdAndId(TenantContext.require(), sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    return new SessionView(
        session.getId(),
        session.getBookingId(),
        session.getStudentId(),
        session.getTutorId(),
        session.getScheduledStart(),
        session.getScheduledEnd(),
        session.getStatus());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SessionSummary> completedSessionsOf(UUID tutorId) {
    Objects.requireNonNull(tutorId, "tutorId must not be null");

    return sessions
        .findByTenantIdAndTutorIdAndStatusOrderByScheduledStartAsc(
            TenantContext.require(), tutorId, SessionStatus.COMPLETED)
        .stream()
        .map(SessionsService::summaryOf)
        .toList();
  }

  private static SessionSummary summaryOf(Session session) {
    return new SessionSummary(
        session.getId(),
        session.getBookingId(),
        session.getStudentId(),
        session.getStartedAt(),
        session.getEndedAt(),
        (int) Duration.between(session.getScheduledStart(), session.getScheduledEnd()).toHours());
  }
}
