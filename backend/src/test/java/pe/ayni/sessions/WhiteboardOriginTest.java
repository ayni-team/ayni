package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * The live channel of a whiteboard accepts the origins of {@code ayni.cors.allowed-origins}, the
 * same list as the REST API, over a real WebSocket against the running backend. The scenario of
 * {@code features/production-packaging.feature} that names this class, with the same display name.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ayni.test.identity-headers=false",
      "ayni.cors.allowed-origins=" + WhiteboardOriginTest.WEB_APPLICATION
    })
class WhiteboardOriginTest {

  static final String WEB_APPLICATION = "https://app.ayni.test";

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final Duration WAIT = Duration.ofSeconds(10);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  @Test
  @DisplayName("Another origin cannot open the live channel of a whiteboard")
  void anotherOriginCannotOpenTheLiveChannelOfAWhiteboard() throws Exception {
    WhiteboardScenario.Pair pair =
        new WhiteboardScenario(jdbc, tenants, users, userSessions, tokens).inProgress();

    // A participant of the session, with a valid token, on a page of another origin.
    assertThatThrownBy(
            () ->
                BoardClient.connectFrom(
                        port, pair.sessionId(), pair.studentToken(), "https://elsewhere.example")
                    .close())
        .hasMessageContaining("403");
    assertThatThrownBy(
            () ->
                BoardClient.connectFrom(
                        port, pair.sessionId(), pair.studentToken(), "http://localhost:5173")
                    .close())
        .hasMessageContaining("403");

    // The same participant from the web application's origin opens it and is sent the board.
    try (BoardClient student =
        BoardClient.connectFrom(port, pair.sessionId(), pair.studentToken(), WEB_APPLICATION)) {
      assertThat(student.awaitBoard(WAIT).message().path("type").asText()).isEqualTo("board");
    }
  }
}
