package pe.ayni.sessions.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.ayni.sessions.domain.model.BoardElement;
import pe.ayni.sessions.domain.model.BoardOperation;
import pe.ayni.sessions.domain.model.WhiteboardRuleViolation;
import pe.ayni.shared.events.WhiteboardClosed;

/**
 * US10 and TS08: drawing on the shared whiteboard of a session in progress.
 *
 * <p>Each change is committed by {@link WhiteboardStore} and only then sent to both participants
 * through the {@link WhiteboardBroadcaster}, with its version. The database lock orders the
 * changes; this class also holds one lock per board of its own around the commit and the sending,
 * so the changes leave in the order of their versions and a participant never receives version 8
 * before version 7 (ADR 0009). The live channel and {@code PUT} both come through here.
 *
 * <p>No transaction here on purpose: a change has to be committed before anybody is told about it,
 * and told before the next change of the same board is.
 */
@Service
public class WhiteboardEditing {

  /** The largest an element may be, as JSON. */
  static final int MAX_ELEMENT_BYTES = 16 * 1024;

  private final WhiteboardStore store;
  private final WhiteboardBroadcaster broadcaster;
  private final ObjectMapper json;
  private final Map<UUID, ReentrantLock> boards = new ConcurrentHashMap<>();

  WhiteboardEditing(WhiteboardStore store, WhiteboardBroadcaster broadcaster, ObjectMapper json) {
    this.store = store;
    this.broadcaster = broadcaster;
    this.json = json;
  }

  /** The board as it is now, for a participant, in any state of the session. */
  public WhiteboardView read(UUID sessionId, UUID userId) {
    return store.read(sessionId, userId);
  }

  /**
   * Whether the person may open the live channel of the board.
   *
   * @throws pe.ayni.sessions.domain.model.NotAParticipant when they are not a participant
   * @throws pe.ayni.sessions.domain.model.SessionNotOpen when the session is not in progress
   */
  public void requireLive(UUID sessionId, UUID userId) {
    store.requireLive(sessionId, userId);
  }

  /**
   * Opens the live channel of a participant: checks they may, and hands them the board while no
   * change of it can be sent, so the board they start from is never newer or older than the first
   * operation they receive after it.
   *
   * @param joined registers the channel and sends it the board, inside the board's order
   * @throws pe.ayni.sessions.domain.model.NotAParticipant when they are not a participant
   * @throws pe.ayni.sessions.domain.model.SessionNotOpen when the session is not in progress
   */
  public void open(UUID sessionId, UUID userId, java.util.function.Consumer<WhiteboardView> joined) {
    inOrder(
        sessionId,
        () -> {
          store.requireLive(sessionId, userId);
          joined.accept(store.read(sessionId, userId));
          return null;
        });
  }

  /**
   * Applies an operation and sends it to both participants.
   *
   * @throws pe.ayni.sessions.domain.model.NotAParticipant when they are not a participant
   * @throws pe.ayni.sessions.domain.model.SessionNotOpen when the session is not in progress
   * @throws WhiteboardRuleViolation when the operation is malformed or the board would be too big
   */
  public AppliedOperation draw(UUID sessionId, UUID userId, BoardOperation operation) {
    Objects.requireNonNull(operation, "operation must not be null");
    if (operation.element() != null) {
      requireSize(operation.element());
    }
    return inOrder(
        sessionId,
        () -> {
          AppliedOperation applied = store.apply(sessionId, userId, operation);
          broadcaster.applied(applied);
          return applied;
        });
  }

  /**
   * Replaces the whole board, when the participant saw its current version.
   *
   * @throws pe.ayni.sessions.domain.model.WhiteboardVersionConflict when it changed since
   */
  public WhiteboardView replace(
      UUID sessionId, UUID userId, List<BoardElement> elements, long seenVersion) {
    Objects.requireNonNull(elements, "elements must not be null");
    elements.forEach(this::requireSize);
    return inOrder(
        sessionId,
        () -> {
          WhiteboardView replaced = store.replace(sessionId, userId, elements, seenVersion);
          broadcaster.replaced(replaced);
          return replaced;
        });
  }

  private <T> T inOrder(UUID sessionId, java.util.function.Supplier<T> change) {
    ReentrantLock lock = boards.computeIfAbsent(sessionId, id -> new ReentrantLock(true));
    lock.lock();
    try {
      return change.get();
    } finally {
      lock.unlock();
    }
  }

  private void requireSize(BoardElement element) {
    try {
      if (json.writeValueAsBytes(element).length > MAX_ELEMENT_BYTES) {
        throw new WhiteboardRuleViolation("An element takes at most 16 KB");
      }
    } catch (JsonProcessingException notJson) {
      throw new WhiteboardRuleViolation("The element cannot be stored");
    }
  }

  /**
   * The session closed: its live channels are closed and its lock forgotten. Once the closing
   * committed, so nobody is sent away from a session that is still on.
   */
  @TransactionalEventListener
  void on(WhiteboardClosed closed) {
    broadcaster.closed(closed.sessionId());
    boards.remove(closed.sessionId());
  }
}
