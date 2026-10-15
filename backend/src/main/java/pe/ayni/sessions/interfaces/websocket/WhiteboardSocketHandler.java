package pe.ayni.sessions.interfaces.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pe.ayni.sessions.application.WhiteboardEditing;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.SessionNotOpen;
import pe.ayni.sessions.domain.model.SessionRuleViolation;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * TS08: the live channel of a whiteboard, where both participants draw at once (ADR 0009).
 *
 * <p>On opening it sends the whole board; after that, every operation applied by either
 * participant, in version order, the sender's own included as its confirmation. An operation that
 * is refused answers an {@code error} with the status HTTP would have given, and the channel stays
 * open; when the session is over it is closed with code 4409.
 *
 * <p>A browser cannot send {@code Authorization} on a WebSocket, so the session token may come as
 * the subprotocol pair {@code bearer, <token>}; this handler accepts the {@code bearer} subprotocol
 * so the browser keeps the connection.
 */
@Component
class WhiteboardSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

  /** How long a send may wait for a slow participant before their channel is dropped. */
  private static final int SEND_TIME_LIMIT_MS = 2000;

  private static final int BUFFER_LIMIT_BYTES = 1024 * 1024;

  private static final String CHANNEL = "channel";

  private final WhiteboardEditing editing;
  private final WhiteboardChannels channels;
  private final ObjectMapper json;

  WhiteboardSocketHandler(
      WhiteboardEditing editing, WhiteboardChannels channels, ObjectMapper json) {
    this.editing = editing;
    this.channels = channels;
    this.json = json;
  }

  @Override
  public List<String> getSubProtocols() {
    return List.of("bearer");
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
    WebSocketSession channel =
        new ConcurrentWebSocketSessionDecorator(raw, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES);
    raw.getAttributes().put(CHANNEL, channel);
    UUID sessionId = sessionOf(raw);
    try {
      as(
          raw,
          () ->
              editing.open(
                  sessionId,
                  userOf(raw),
                  board -> {
                    channels.join(sessionId, channel);
                    channels.sendBoard(channel, board);
                  }));
    } catch (SessionNotOpen | NotAParticipant | NoSuchElementException refused) {
      // It closed between the handshake and now.
      raw.close(WhiteboardChannels.SESSION_OVER);
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws Exception {
    WebSocketSession channel = channelOf(raw);
    WhiteboardMessages.Incoming incoming;
    try {
      incoming = json.readValue(message.getPayload(), WhiteboardMessages.Incoming.class);
    } catch (JsonProcessingException malformed) {
      channels.send(channel, WhiteboardMessages.error(400, reasonOf(malformed)));
      return;
    }
    if (!"operation".equals(incoming.type()) || incoming.operation() == null) {
      channels.send(
          channel, WhiteboardMessages.error(400, "Send {\"type\": \"operation\", \"operation\": ...}"));
      return;
    }
    try {
      as(raw, () -> editing.draw(sessionOf(raw), userOf(raw), incoming.operation()));
    } catch (SessionNotOpen over) {
      channels.send(channel, WhiteboardMessages.error(409, over.getMessage()));
      raw.close(WhiteboardChannels.SESSION_OVER);
    } catch (NotAParticipant stranger) {
      channels.send(channel, WhiteboardMessages.error(403, stranger.getMessage()));
    } catch (SessionRuleViolation refused) {
      channels.send(channel, WhiteboardMessages.error(400, refused.getMessage()));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
    channels.leave(sessionOf(raw), channelOf(raw));
  }

  /** Binds the university and the person of the channel while its work runs. */
  private static void as(WebSocketSession raw, Runnable work) {
    TenantContext.runAs(
        (String) raw.getAttributes().get(WhiteboardHandshake.TENANT),
        () -> CurrentUser.runAs(userOf(raw), work));
  }

  private static UUID sessionOf(WebSocketSession raw) {
    return (UUID) raw.getAttributes().get(WhiteboardHandshake.SESSION);
  }

  private static UUID userOf(WebSocketSession raw) {
    return (UUID) raw.getAttributes().get(WhiteboardHandshake.USER);
  }

  private static WebSocketSession channelOf(WebSocketSession raw) {
    Object channel = raw.getAttributes().get(CHANNEL);
    return channel instanceof WebSocketSession decorated ? decorated : raw;
  }

  /** The message of the rule an element broke, rather than the parser's. */
  private static String reasonOf(JsonProcessingException malformed) {
    Throwable cause = malformed;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause instanceof SessionRuleViolation rule
        ? rule.getMessage()
        : "The message is not a whiteboard operation";
  }
}
