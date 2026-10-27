package pe.ayni.sessions.interfaces.websocket;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import pe.ayni.sessions.application.WhiteboardEditing;
import pe.ayni.sessions.domain.model.NotAParticipant;
import pe.ayni.sessions.domain.model.SessionNotOpen;
import pe.ayni.shared.tenancy.CurrentUser;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * Lets into the live channel of a whiteboard only a participant of a session in progress.
 *
 * <p>The handshake is an HTTP request like any other, so the access filter has already resolved
 * its session token (TS03) and bound its university and its person. They are kept with the
 * channel, because its messages arrive later on other threads, where nothing is bound. A refusal
 * answers the handshake with the status the same request over HTTP would get: 404, 403 or 409.
 */
@Component
class WhiteboardHandshake implements HandshakeInterceptor {

  static final String TENANT = "tenantId";
  static final String USER = "userId";
  static final String SESSION = "sessionId";

  private final WhiteboardEditing editing;

  WhiteboardHandshake(WhiteboardEditing editing) {
    this.editing = editing;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {

    String tenantId = TenantContext.get();
    UUID userId = CurrentUser.get();
    if (tenantId == null || userId == null) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    UUID sessionId = sessionIdOf(request.getURI().getPath());
    if (sessionId == null) {
      response.setStatusCode(HttpStatus.BAD_REQUEST);
      return false;
    }
    try {
      editing.requireLive(sessionId, userId);
    } catch (NoSuchElementException notHere) {
      response.setStatusCode(HttpStatus.NOT_FOUND);
      return false;
    } catch (NotAParticipant stranger) {
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return false;
    } catch (SessionNotOpen notInProgress) {
      response.setStatusCode(HttpStatus.CONFLICT);
      return false;
    }
    attributes.put(TENANT, tenantId);
    attributes.put(USER, userId);
    attributes.put(SESSION, sessionId);
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {
    // Nothing to do once the channel is open.
  }

  /** The session of {@code /api/v1/sessions/{id}/whiteboard/live}. */
  static UUID sessionIdOf(String path) {
    String[] segments = path.split("/");
    for (int i = 0; i < segments.length - 1; i++) {
      if (segments[i].equals("sessions")) {
        try {
          return UUID.fromString(segments[i + 1]);
        } catch (IllegalArgumentException notAUuid) {
          return null;
        }
      }
    }
    return null;
  }
}
