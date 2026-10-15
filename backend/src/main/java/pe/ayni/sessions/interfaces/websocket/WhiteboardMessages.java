package pe.ayni.sessions.interfaces.websocket;

import java.util.List;
import java.util.UUID;
import pe.ayni.sessions.application.AppliedOperation;
import pe.ayni.sessions.application.WhiteboardView;
import pe.ayni.sessions.domain.model.BoardElement;
import pe.ayni.sessions.domain.model.BoardOperation;

/**
 * What travels on the live channel of a whiteboard, as JSON with a {@code type} (ADR 0009).
 *
 * <ul>
 *   <li>From the client: {@code {"type": "operation", "operation": {...}}}.
 *   <li>From the server: {@code board}, the whole board and its version, first of all and after a
 *       replacement; {@code applied}, an operation with its version and who sent it, to both
 *       participants; {@code error}, with the status an HTTP request would have answered.
 * </ul>
 */
final class WhiteboardMessages {

  private WhiteboardMessages() {}

  /** What a client sends. */
  record Incoming(String type, BoardOperation operation) {}

  record Board(
      String type, UUID sessionId, long version, List<BoardElement> elements, boolean hasActivity) {}

  record Applied(String type, UUID sessionId, long version, UUID by, BoardOperation operation) {}

  record Error(String type, int status, String message) {}

  static Board board(WhiteboardView board) {
    return new Board(
        "board", board.sessionId(), board.version(), board.elements(), board.hasActivity());
  }

  static Applied applied(AppliedOperation applied) {
    return new Applied(
        "applied", applied.sessionId(), applied.version(), applied.by(), applied.operation());
  }

  static Error error(int status, String message) {
    return new Error("error", status, message);
  }
}
