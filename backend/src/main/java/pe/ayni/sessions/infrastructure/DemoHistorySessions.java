package pe.ayni.sessions.infrastructure;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pe.ayni.sessions.application.AbandonTutorNoShowUseCase;
import pe.ayni.sessions.application.ConfirmPresenceUseCase;
import pe.ayni.sessions.application.EndSessionUseCase;
import pe.ayni.sessions.application.IssuePresenceCodesUseCase;
import pe.ayni.sessions.application.JoinSessionUseCase;
import pe.ayni.sessions.application.WhiteboardEditing;
import pe.ayni.sessions.domain.model.BoardElement;
import pe.ayni.sessions.domain.model.BoardOperation;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.shared.demo.DemoSessions;
import pe.ayni.shared.events.PresenceCodeIssued;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Sessions' part of the history of the {@code demo} profile: each step is the use case of the
 * endpoint a participant calls, or of the job that would run at that time.
 *
 * <p>The presence code travels only by email. Here it is read where the acceptance tests read it,
 * from {@link PresenceCodeIssued} as it is published, and only for a session this history is
 * confirming: nothing is stored, nothing is answered over HTTP, and the class does not exist
 * outside the {@code demo} profile. The code is confirmed with the real use case, which compares
 * it with the hash it keeps.
 */
@Component
@Profile("demo")
class DemoHistorySessions implements DemoSessions {

  private final SessionRepository sessions;
  private final JoinSessionUseCase join;
  private final IssuePresenceCodesUseCase issuePresenceCodes;
  private final ConfirmPresenceUseCase confirmPresence;
  private final EndSessionUseCase end;
  private final AbandonTutorNoShowUseCase abandonNoShows;
  private final WhiteboardEditing whiteboard;

  /** The codes of the session being confirmed, by person; empty the rest of the time. */
  private final Map<UUID, String> codes = new ConcurrentHashMap<>();

  private volatile UUID confirming;

  DemoHistorySessions(
      SessionRepository sessions,
      JoinSessionUseCase join,
      IssuePresenceCodesUseCase issuePresenceCodes,
      ConfirmPresenceUseCase confirmPresence,
      EndSessionUseCase end,
      AbandonTutorNoShowUseCase abandonNoShows,
      WhiteboardEditing whiteboard) {
    this.sessions = sessions;
    this.join = join;
    this.issuePresenceCodes = issuePresenceCodes;
    this.confirmPresence = confirmPresence;
    this.end = end;
    this.abandonNoShows = abandonNoShows;
    this.whiteboard = whiteboard;
  }

  @EventListener
  void on(PresenceCodeIssued issued) {
    if (issued.sessionId().equals(confirming)) {
      codes.put(issued.userId(), issued.code());
    }
  }

  @Override
  public UUID sessionOf(UUID bookingId) {
    return sessions
        .findByTenantIdAndBookingId(TenantContext.require(), bookingId)
        .map(Session::getId)
        .orElseThrow(() -> new NoSuchElementException("No session for booking " + bookingId));
  }

  @Override
  public void join(UUID sessionId, UUID userId) {
    join.execute(sessionId, userId);
  }

  @Override
  public void confirmPresence(UUID sessionId) {
    Session session =
        sessions
            .findByTenantIdAndId(TenantContext.require(), sessionId)
            .orElseThrow(() -> new NoSuchElementException("No session " + sessionId));
    confirming = sessionId;
    try {
      // The job may have issued them a moment earlier; then they are sent again, which publishes
      // the event once more for each.
      if (!issuePresenceCodes.issueFor(sessionId)) {
        issuePresenceCodes.resend(sessionId, session.getStudentId());
        issuePresenceCodes.resend(sessionId, session.getTutorId());
      }
      confirmPresence.execute(sessionId, session.getStudentId(), codeOf(session.getStudentId()));
      confirmPresence.execute(sessionId, session.getTutorId(), codeOf(session.getTutorId()));
    } finally {
      confirming = null;
      codes.clear();
    }
  }

  @Override
  public void draw(
      UUID sessionId, UUID userId, String elementId, String type, Map<String, Object> data) {
    whiteboard.draw(
        sessionId,
        userId,
        new BoardOperation(
            elementId + "-put",
            BoardOperation.Action.PUT_ELEMENT,
            new BoardElement(elementId, type, data),
            null));
  }

  @Override
  public void end(UUID sessionId, UUID userId) {
    end.execute(sessionId, userId);
  }

  @Override
  public void abandonIfAbsent(UUID sessionId) {
    abandonNoShows.abandonFor(sessionId);
  }

  private String codeOf(UUID userId) {
    String code = codes.get(userId);
    if (code == null) {
      throw new IllegalStateException("No presence code was issued to " + userId);
    }
    return code;
  }
}
