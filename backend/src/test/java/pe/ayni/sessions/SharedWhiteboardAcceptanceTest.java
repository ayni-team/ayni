package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.WhiteboardClosed;

/**
 * US10 over HTTP and real WebSockets against a real PostgreSQL, with real session tokens. One test
 * per scenario of {@code features/US10-shared-whiteboard.feature}, with the same name.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "ayni.test.identity-headers=false")
@Import(SharedWhiteboardAcceptanceTest.Closings.class)
class SharedWhiteboardAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final Duration WAIT = Duration.ofSeconds(5);

  /** Every {@link WhiteboardClosed} the application publishes, whatever thread publishes it. */
  @TestConfiguration(proxyBeanMethods = false)
  static class Closings {
    final List<WhiteboardClosed> published = new CopyOnWriteArrayList<>();

    @EventListener
    void on(WhiteboardClosed closed) {
      published.add(closed);
    }
  }

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private Closings closings;

  private WhiteboardScenario scenario;

  @BeforeEach
  void theScenario() {
    scenario = new WhiteboardScenario(jdbc, tenants, users, userSessions, tokens);
  }

  private ResponseEntity<String> call(
      HttpMethod method, String path, String token, String body) {
    HttpHeaders headers = new HttpHeaders();
    if (token != null) {
      headers.setBearerAuth(token);
    }
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
  }

  private JsonNode readBoard(UUID session, String token) throws Exception {
    ResponseEntity<String> answer =
        call(HttpMethod.GET, "/api/v1/sessions/" + session + "/whiteboard", token, null);
    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
    return json.readTree(answer.getBody());
  }

  private void end(UUID session, String token) {
    assertThat(
            call(HttpMethod.POST, "/api/v1/sessions/" + session + "/end", token, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  private static List<String> idsOf(JsonNode board) {
    List<String> ids = new java.util.ArrayList<>();
    board.path("elements").forEach(element -> ids.add(element.path("id").asText()));
    return ids;
  }

  @Test
  @DisplayName("Both participants draw at the same time")
  void bothParticipantsDrawAtTheSameTime() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    try (BoardClient student = BoardClient.connect(port, pair.sessionId(), pair.studentToken());
        BoardClient tutor =
            BoardClient.connectWithHeader(port, pair.sessionId(), pair.tutorToken())) {
      student.awaitBoard(WAIT);
      tutor.awaitBoard(WAIT);

      CyclicBarrier together = new CyclicBarrier(2);
      ExecutorService both = Executors.newFixedThreadPool(2);
      Future<List<String>> byStudent =
          both.submit(() -> drawSeveral(student, "student", together));
      Future<List<String>> byTutor = both.submit(() -> drawSeveral(tutor, "tutor", together));
      List<String> studentOps = byStudent.get();
      List<String> tutorOps = byTutor.get();
      both.shutdown();

      for (String operation : studentOps) {
        tutor.awaitApplied(operation, WAIT);
        student.awaitApplied(operation, WAIT);
      }
      for (String operation : tutorOps) {
        student.awaitApplied(operation, WAIT);
        tutor.awaitApplied(operation, WAIT);
      }

      Map<String, JsonNode> seenByStudent = student.board();
      assertThat(seenByStudent).containsKeys("student-0", "student-4", "tutor-0", "tutor-4");
      assertThat(seenByStudent.keySet()).isEqualTo(tutor.board().keySet());
      assertThat(idsOf(readBoard(pair.sessionId(), pair.studentToken())))
          .containsExactlyElementsOf(seenByStudent.keySet());
    }
  }

  private static List<String> drawSeveral(BoardClient client, String who, CyclicBarrier together)
      throws Exception {
    together.await();
    List<String> operations = new java.util.ArrayList<>();
    for (int i = 0; i < 5; i++) {
      operations.add(client.draw(who + "-" + i));
    }
    return operations;
  }

  @Test
  @DisplayName("The content is intact after reconnecting")
  void theContentIsIntactAfterReconnecting() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    try (BoardClient tutor = BoardClient.connect(port, pair.sessionId(), pair.tutorToken())) {
      tutor.awaitBoard(WAIT);

      BoardClient student = BoardClient.connect(port, pair.sessionId(), pair.studentToken());
      student.awaitBoard(WAIT);
      student.awaitApplied(student.draw("before-the-drop"), WAIT);
      student.close();

      String whileAway = tutor.draw("while-away");
      tutor.awaitApplied(whileAway, WAIT);
      tutor.awaitApplied(tutor.remove("nothing-there"), WAIT);

      try (BoardClient back = BoardClient.connect(port, pair.sessionId(), pair.studentToken())) {
        JsonNode board = back.awaitBoard(WAIT).message();
        assertThat(idsOf(board)).containsExactly("before-the-drop", "while-away");
        assertThat(board.path("version").asLong()).isEqualTo(3);
        assertThat(back.board().keySet()).isEqualTo(tutor.board().keySet());
      }
    }
  }

  @Test
  @DisplayName("The content is kept when the session closes")
  void theContentIsKeptWhenTheSessionCloses() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    try (BoardClient student = BoardClient.connect(port, pair.sessionId(), pair.studentToken());
        BoardClient tutor = BoardClient.connect(port, pair.sessionId(), pair.tutorToken())) {
      student.awaitBoard(WAIT);
      tutor.awaitBoard(WAIT);
      student.awaitApplied(student.draw("a-graph"), WAIT);
      tutor.awaitApplied(tutor.draw("a-formula"), WAIT);

      end(pair.sessionId(), pair.studentToken());
      end(pair.sessionId(), pair.tutorToken());

      assertThat(student.awaitClosed(WAIT).getCode()).isEqualTo(4409);
      assertThat(tutor.awaitClosed(WAIT).getCode()).isEqualTo(4409);
    }
    assertThat(scenario.statusOf(pair.sessionId())).isIn("COMPLETED", "UNVERIFIED");

    Map<String, Object> stored = scenario.storedBoard(pair.sessionId());
    assertThat(stored.get("has_activity")).isEqualTo(true);
    assertThat(json.readTree((String) stored.get("content")).path("elements")).hasSize(2);

    JsonNode afterwards = readBoard(pair.sessionId(), pair.tutorToken());
    assertThat(idsOf(afterwards)).containsExactly("a-graph", "a-formula");
    assertThat(afterwards.path("hasActivity").asBoolean()).isTrue();
    assertThat(afterwards.path("live").asBoolean()).isFalse();

    assertThat(
            call(
                    HttpMethod.PUT,
                    "/api/v1/sessions/" + pair.sessionId() + "/whiteboard",
                    pair.studentToken(),
                    "{\"version\": 2, \"elements\": []}")
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThatThrownBy(() -> BoardClient.connect(port, pair.sessionId(), pair.studentToken()))
        .hasStackTraceContaining("409");
    assertThat(closings.published)
        .filteredOn(closed -> closed.sessionId().equals(pair.sessionId()))
        .singleElement()
        .satisfies(closed -> assertThat(closed.hadActivity()).isTrue());
  }

  @Test
  @DisplayName("A session without activity is recorded as such")
  void aSessionWithoutActivityIsRecordedAsSuch() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();

    end(pair.sessionId(), pair.studentToken());
    end(pair.sessionId(), pair.tutorToken());

    Map<String, Object> stored = scenario.storedBoard(pair.sessionId());
    assertThat(stored.get("has_activity")).isEqualTo(false);
    JsonNode board = readBoard(pair.sessionId(), pair.studentToken());
    assertThat(board.path("elements")).isEmpty();
    assertThat(board.path("version").asLong()).isZero();
    assertThat(board.path("hasActivity").asBoolean()).isFalse();
    assertThat(closings.published)
        .filteredOn(closed -> closed.sessionId().equals(pair.sessionId()))
        .singleElement()
        .satisfies(
            closed -> {
              assertThat(closed.hadActivity()).isFalse();
              assertThat(closed.tenantId()).isEqualTo(pair.tenantId());
              assertThat(closed.tutorId()).isEqualTo(pair.tutor());
              assertThat(closed.studentId()).isEqualTo(pair.student());
            });
  }

  @Test
  @DisplayName("Only the participants of a session in progress open the live channel")
  void onlyTheParticipantsOfASessionInProgressOpenTheLiveChannel() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    UUID stranger = scenario.aStudent(pair.tenantId());
    String elsewhere = scenario.aUniversity();
    UUID ofAnotherUniversity = scenario.aStudent(elsewhere);
    UUID notStarted =
        scenario.aSession(pair.tenantId(), pair.student(), pair.tutor(), "SCHEDULED");

    assertThatThrownBy(() -> BoardClient.connect(port, pair.sessionId(), "nobody-issued-this"))
        .hasStackTraceContaining("401");
    assertThatThrownBy(
            () ->
                BoardClient.connect(
                    port, pair.sessionId(), scenario.tokenOf(pair.tenantId(), stranger)))
        .hasStackTraceContaining("403");
    assertThatThrownBy(
            () ->
                BoardClient.connect(
                    port, pair.sessionId(), scenario.tokenOf(elsewhere, ofAnotherUniversity)))
        .hasStackTraceContaining("404");
    assertThatThrownBy(() -> BoardClient.connect(port, notStarted, pair.studentToken()))
        .hasStackTraceContaining("409");

    // Over HTTP the same people are refused the same way, and nobody reads somebody else's board.
    String path = "/api/v1/sessions/" + pair.sessionId() + "/whiteboard";
    assertThat(call(HttpMethod.GET, path, null, null).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            call(HttpMethod.GET, path, scenario.tokenOf(pair.tenantId(), stranger), null)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            call(HttpMethod.GET, path, scenario.tokenOf(elsewhere, ofAnotherUniversity), null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Replacing the whole board needs the version last read")
  void replacingTheWholeBoardNeedsTheVersionLastRead() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    String path = "/api/v1/sessions/" + pair.sessionId() + "/whiteboard";
    try (BoardClient tutor = BoardClient.connect(port, pair.sessionId(), pair.tutorToken())) {
      tutor.awaitBoard(WAIT);
      long read = readBoard(pair.sessionId(), pair.studentToken()).path("version").asLong();
      tutor.awaitApplied(tutor.draw("drawn-meanwhile"), WAIT);

      String replacement =
          """
          {"version": %d, "elements": [{"id": "clean", "type": "text", "data": {"text": "x"}}]}
          """;
      ResponseEntity<String> stale =
          call(HttpMethod.PUT, path, pair.studentToken(), replacement.formatted(read));
      assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(idsOf(readBoard(pair.sessionId(), pair.studentToken())))
          .containsExactly("drawn-meanwhile");

      ResponseEntity<String> current =
          call(HttpMethod.PUT, path, pair.studentToken(), replacement.formatted(read + 1));
      assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(idsOf(json.readTree(current.getBody()))).containsExactly("clean");
      JsonNode received =
          tutor
              .await(
                  message ->
                      "board".equals(message.path("type").asText())
                          && message.path("version").asLong() == read + 2,
                  WAIT)
              .message();
      assertThat(idsOf(received)).containsExactly("clean");
    }
  }

  @Test
  @DisplayName("An operation the board refuses keeps the channel open")
  void anOperationTheBoardRefusesKeepsTheChannelOpen() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    try (BoardClient student = BoardClient.connect(port, pair.sessionId(), pair.studentToken())) {
      student.awaitBoard(WAIT);
      student.send(
          """
          {"type": "operation", "operation": {"id": "bad", "action": "PUT_ELEMENT",
           "element": {"id": "no-type"}}}
          """);
      JsonNode error = student.awaitError(WAIT).message();
      assertThat(error.path("status").asInt()).isEqualTo(400);
      assertThat(error.path("message").asText()).contains("type");

      student.awaitApplied(student.draw("fine"), WAIT);
      assertThat(student.isOpen()).isTrue();
      assertThat(idsOf(readBoard(pair.sessionId(), pair.studentToken()))).containsExactly("fine");
    }
  }
}
