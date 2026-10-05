package pe.ayni.recognition.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pe.ayni.recognition.infrastructure.RequestedSessionRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * The verified sessions a student taught that no request has used yet, the oldest first.
 *
 * <p>These are the hours that count towards the next request, both when the student looks at their
 * progress and when they submit it: a session backs one request and never a second one.
 */
@Component
class UnclaimedSessions {

  private final SessionsApi sessions;
  private final RequestedSessionRepository requestedSessions;

  UnclaimedSessions(SessionsApi sessions, RequestedSessionRepository requestedSessions) {
    this.sessions = sessions;
    this.requestedSessions = requestedSessions;
  }

  List<SessionSummary> of(UUID studentId) {
    List<SessionSummary> taught = sessions.completedSessionsOf(studentId);
    if (taught.isEmpty()) {
      return taught;
    }
    Set<UUID> claimed =
        Set.copyOf(
            requestedSessions.findClaimed(
                TenantContext.require(), taught.stream().map(SessionSummary::sessionId).toList()));
    return taught.stream().filter(session -> !claimed.contains(session.sessionId())).toList();
  }
}
