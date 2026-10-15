package pe.ayni.sessions.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.domain.model.BoardContent;
import pe.ayni.sessions.domain.model.BoardElement;
import pe.ayni.sessions.domain.model.BoardOperation;
import pe.ayni.sessions.domain.model.Session;
import pe.ayni.sessions.domain.model.Whiteboard;
import pe.ayni.sessions.infrastructure.SessionRepository;
import pe.ayni.sessions.infrastructure.WhiteboardRepository;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Reads and changes a whiteboard, one transaction each. {@link WhiteboardEditing} calls it, so the
 * change is committed before it is told to anybody.
 *
 * <p>A change locks the session first. That is what gives two operations sent at once an order,
 * and the same lock is taken by joining, ending and closing the session, so a board never changes
 * after its session closed.
 */
@Component
class WhiteboardStore {

  private final SessionRepository sessions;
  private final WhiteboardRepository whiteboards;
  private final Clock clock;

  WhiteboardStore(SessionRepository sessions, WhiteboardRepository whiteboards, Clock clock) {
    this.sessions = sessions;
    this.whiteboards = whiteboards;
    this.clock = clock;
  }

  /**
   * @throws NoSuchElementException when the session does not exist in the current university
   * @throws pe.ayni.sessions.domain.model.NotAParticipant when the reader is not a participant
   */
  @Transactional(readOnly = true)
  public WhiteboardView read(UUID sessionId, UUID userId) {
    String tenantId = TenantContext.require();
    Session session = find(tenantId, sessionId);
    session.roleOf(userId);
    return whiteboards
        .findByTenantIdAndSessionId(tenantId, sessionId)
        .map(board -> view(session, board))
        .orElseGet(
            () ->
                new WhiteboardView(
                    sessionId,
                    BoardContent.EMPTY.version(),
                    List.of(),
                    false,
                    isLive(session),
                    null));
  }

  /**
   * Who may open the live channel: a participant of a session in progress.
   *
   * @throws pe.ayni.sessions.domain.model.SessionNotOpen when the session is not in progress
   */
  @Transactional(readOnly = true)
  public void requireLive(UUID sessionId, UUID userId) {
    Session session = find(TenantContext.require(), sessionId);
    session.roleOf(userId);
    session.requireInProgressToDraw();
  }

  @Transactional
  public AppliedOperation apply(UUID sessionId, UUID userId, BoardOperation operation) {
    Whiteboard board = lockForDrawing(sessionId, userId);
    long version = board.apply(operation, clock.instant());
    whiteboards.save(board);
    return new AppliedOperation(sessionId, version, operation, userId);
  }

  @Transactional
  public WhiteboardView replace(
      UUID sessionId, UUID userId, List<BoardElement> elements, long seenVersion) {
    Whiteboard board = lockForDrawing(sessionId, userId);
    board.replace(elements, seenVersion, clock.instant());
    whiteboards.save(board);
    Session session = find(TenantContext.require(), sessionId);
    return view(session, board);
  }

  private Whiteboard lockForDrawing(UUID sessionId, UUID userId) {
    String tenantId = TenantContext.require();
    Session session =
        sessions
            .lockByTenantIdAndId(tenantId, sessionId)
            .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
    session.roleOf(userId);
    session.requireInProgressToDraw();
    Instant now = clock.instant();
    return whiteboards
        .findByTenantIdAndSessionId(tenantId, sessionId)
        .orElseGet(() -> Whiteboard.emptyFor(session, now));
  }

  private Session find(String tenantId, UUID sessionId) {
    return sessions
        .findByTenantIdAndId(tenantId, sessionId)
        .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionId));
  }

  private static boolean isLive(Session session) {
    return session.getStatus() == SessionStatus.IN_PROGRESS;
  }

  private static WhiteboardView view(Session session, Whiteboard board) {
    BoardContent content = board.content();
    return new WhiteboardView(
        session.getId(),
        content.version(),
        content.elements(),
        board.hasActivity(),
        isLive(session),
        board.getUpdatedAt());
  }
}
