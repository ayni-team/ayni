package pe.ayni.sessions.interfaces.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Where the live channel of the whiteboards answers: {@code
 * /api/v1/sessions/{id}/whiteboard/live} (TS08, ADR 0009).
 *
 * <p>The largest message a channel takes, 256 KB, is set in {@code application.yml} as the servlet
 * context parameter Tomcat reads, {@code org.apache.tomcat.websocket.textBufferSize}: Tomcat's own
 * default of 8 KB would close the channel of whoever draws a long stroke.
 *
 * <p>A browser opens the channel only from an origin of {@code ayni.cors.allowed-origins}, the same
 * list the REST API accepts; the handshake from any other origin is refused.
 */
@Configuration
@EnableWebSocket
class WhiteboardSocketConfig implements WebSocketConfigurer {

  private final WhiteboardSocketHandler handler;
  private final WhiteboardHandshake handshake;
  private final String[] allowedOrigins;

  WhiteboardSocketConfig(
      WhiteboardSocketHandler handler,
      WhiteboardHandshake handshake,
      @Value("${ayni.cors.allowed-origins}") String[] allowedOrigins) {
    this.handler = handler;
    this.handshake = handshake;
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/api/v1/sessions/*/whiteboard/live")
        .addInterceptors(handshake)
        .setAllowedOrigins(allowedOrigins);
  }
}
