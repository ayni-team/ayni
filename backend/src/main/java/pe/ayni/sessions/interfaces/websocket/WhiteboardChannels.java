package pe.ayni.sessions.interfaces.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import pe.ayni.sessions.application.AppliedOperation;
import pe.ayni.sessions.application.WhiteboardBroadcaster;
import pe.ayni.sessions.application.WhiteboardView;

/**
 * The open live channels of every whiteboard, and what is sent through them (ADR 0009).
 *
 * <p>They live in the memory of this instance: the modular monolith runs as one. A channel that
 * cannot be written to is dropped; its participant reconnects and receives the whole board again.
 */
@Component
class WhiteboardChannels implements WhiteboardBroadcaster {

  /** Closes a channel when its session is over: a code of the application's range. */
  static final CloseStatus SESSION_OVER = new CloseStatus(4409, "The session is over");

  private static final Logger log = LoggerFactory.getLogger(WhiteboardChannels.class);

  private final Map<UUID, Set<WebSocketSession>> channels = new ConcurrentHashMap<>();
  private final ObjectMapper json;

  WhiteboardChannels(ObjectMapper json) {
    this.json = json;
  }

  void join(UUID sessionId, WebSocketSession channel) {
    channels.computeIfAbsent(sessionId, id -> ConcurrentHashMap.newKeySet()).add(channel);
  }

  void leave(UUID sessionId, WebSocketSession channel) {
    channels.computeIfPresent(
        sessionId,
        (id, open) -> {
          open.remove(channel);
          return open.isEmpty() ? null : open;
        });
  }

  /** The whole board, the first thing a channel receives and what a replacement sends. */
  void sendBoard(WebSocketSession channel, WhiteboardView board) {
    send(channel, WhiteboardMessages.board(board));
  }

  @Override
  public void applied(AppliedOperation operation) {
    sendToAll(operation.sessionId(), WhiteboardMessages.applied(operation));
  }

  @Override
  public void replaced(WhiteboardView board) {
    sendToAll(board.sessionId(), WhiteboardMessages.board(board));
  }

  @Override
  public void closed(UUID sessionId) {
    Set<WebSocketSession> open = channels.remove(sessionId);
    if (open == null) {
      return;
    }
    for (WebSocketSession channel : open) {
      try {
        channel.close(SESSION_OVER);
      } catch (IOException alreadyGone) {
        // Nothing left to tell it.
      }
    }
  }

  private void sendToAll(UUID sessionId, Object message) {
    for (WebSocketSession channel : channels.getOrDefault(sessionId, Set.of())) {
      send(channel, message);
    }
  }

  void send(WebSocketSession channel, Object message) {
    try {
      channel.sendMessage(new TextMessage(json.writeValueAsString(message)));
    } catch (JsonProcessingException notJson) {
      throw new IllegalStateException("A whiteboard message could not be written", notJson);
    } catch (IOException | IllegalStateException gone) {
      // A channel that cannot be written to is dropped; the participant reconnects to the board.
      log.debug("Dropping a whiteboard channel that could not be written to: {}", gone.toString());
      channels.values().forEach(open -> open.remove(channel));
      try {
        channel.close(CloseStatus.SESSION_NOT_RELIABLE);
      } catch (IOException closing) {
        // Already closed.
      }
    }
  }
}
