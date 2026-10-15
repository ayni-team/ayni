package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

/**
 * TS08 with real WebSocket clients against the running backend and a real PostgreSQL. One test per
 * scenario of {@code features/TS08-whiteboard-real-time.feature}, with the same name.
 *
 * <p>Times are measured by the clients in this JVM, from the moment one participant sends an
 * operation to the moment the other receives it, so both ends read the same clock. Each
 * measurement is taken after a few warm up operations, so what is measured is the board and not
 * the first compilation of its code.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "ayni.test.identity-headers=false")
class WhiteboardRealTimeTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = SessionsTestDatabase.INSTANCE;

  private static final Duration SEEN_WITHIN = Duration.ofMillis(300);
  private static final Duration REJOINED_WITHIN = Duration.ofSeconds(3);
  private static final Duration WAIT = Duration.ofSeconds(10);

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(WhiteboardRealTimeTest.class);

  private WhiteboardScenario scenario;

  private static void report(String what, List<Double> latencies) {
    List<Double> sorted = latencies.stream().sorted().toList();
    log.info(
        "TS08 {}: {} changes, median {} ms, p95 {} ms, max {} ms",
        what,
        sorted.size(),
        String.format("%.1f", sorted.get(sorted.size() / 2)),
        String.format("%.1f", sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1)),
        String.format("%.1f", sorted.getLast()));
  }

  @BeforeEach
  void theScenario() {
    scenario = new WhiteboardScenario(jdbc, tenants, users, userSessions, tokens);
  }

  private static void warmUp(BoardClient from, BoardClient to) throws Exception {
    for (int i = 0; i < 10; i++) {
      to.awaitApplied(from.draw("warm-up-" + i), WAIT);
    }
  }

  /** Milliseconds from sending an operation to the other participant receiving it. */
  private static double millisToTheOther(BoardClient from, BoardClient to, String elementId)
      throws Exception {
    long sent = System.nanoTime();
    String operation = from.draw(elementId);
    long received = to.awaitApplied(operation, WAIT).atNanos();
    return (received - sent) / 1_000_000.0;
  }

  @Test
  @DisplayName("The other participant sees a change in less than 300 ms")
  void theOtherParticipantSeesAChangeInLessThan300Ms() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    try (BoardClient student = BoardClient.connect(port, pair.sessionId(), pair.studentToken());
        BoardClient tutor = BoardClient.connect(port, pair.sessionId(), pair.tutorToken())) {
      student.awaitBoard(WAIT);
      tutor.awaitBoard(WAIT);
      warmUp(student, tutor);

      List<Double> latencies = new ArrayList<>();
      for (int i = 0; i < 30; i++) {
        latencies.add(millisToTheOther(student, tutor, "student-" + i));
        latencies.add(millisToTheOther(tutor, student, "tutor-" + i));
      }

      report("one session", latencies);
      assertThat(latencies).allSatisfy(ms -> assertThat(ms).isLessThan(SEEN_WITHIN.toMillis()));
    }
  }

  @Test
  @DisplayName("A participant who reconnects has the whole board again in less than 3 s")
  void aParticipantWhoReconnectsHasTheWholeBoardAgainInLessThan3S() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    // A full board: a thousand strokes of a few dozen points each.
    StringBuilder elements = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      elements.append(i == 0 ? "" : ",");
      elements.append("{\"id\": \"stroke-").append(i).append("\", \"type\": \"stroke\",");
      elements.append(" \"data\": {\"points\": [");
      for (int p = 0; p < 40; p++) {
        elements.append(p == 0 ? "" : ",").append("[").append(p).append(",").append(i).append("]");
      }
      elements.append("]}}");
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(pair.tutorToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> filled =
        http.exchange(
            "/api/v1/sessions/" + pair.sessionId() + "/whiteboard",
            HttpMethod.PUT,
            new HttpEntity<>("{\"version\": 0, \"elements\": [" + elements + "]}", headers),
            String.class);
    assertThat(filled.getStatusCode()).isEqualTo(HttpStatus.OK);

    try (BoardClient tutor = BoardClient.connect(port, pair.sessionId(), pair.tutorToken())) {
      tutor.awaitBoard(WAIT);
      BoardClient student = BoardClient.connect(port, pair.sessionId(), pair.studentToken());
      student.awaitBoard(WAIT);
      student.close();
      tutor.awaitApplied(tutor.draw("while-away"), WAIT);

      long reconnecting = System.nanoTime();
      try (BoardClient back = BoardClient.connect(port, pair.sessionId(), pair.studentToken())) {
        JsonNode board = back.awaitBoard(WAIT).message();
        Duration took = Duration.ofNanos(System.nanoTime() - reconnecting);
        log.info("TS08 reconnection to a board of 1001 elements: {} ms", took.toMillis());

        assertThat(took).isLessThan(REJOINED_WITHIN);
        assertThat(board.path("elements")).hasSize(1001);
        assertThat(board.path("version").asLong()).isEqualTo(2);
      }
    }
  }

  @Test
  @DisplayName("Drawing at the same time ends with both seeing the same board")
  void drawingAtTheSameTimeEndsWithBothSeeingTheSameBoard() throws Exception {
    WhiteboardScenario.Pair pair = scenario.inProgress();
    try (BoardClient student = BoardClient.connect(port, pair.sessionId(), pair.studentToken());
        BoardClient tutor = BoardClient.connect(port, pair.sessionId(), pair.tutorToken())) {
      student.awaitBoard(WAIT);
      tutor.awaitBoard(WAIT);

      CyclicBarrier together = new CyclicBarrier(2);
      ExecutorService both = Executors.newFixedThreadPool(2);
      Future<List<String>> byStudent = both.submit(() -> contend(student, "student", together));
      Future<List<String>> byTutor = both.submit(() -> contend(tutor, "tutor", together));
      List<String> operations = new ArrayList<>(byStudent.get(30, TimeUnit.SECONDS));
      operations.addAll(byTutor.get(30, TimeUnit.SECONDS));
      both.shutdown();
      for (String operation : operations) {
        student.awaitApplied(operation, WAIT);
        tutor.awaitApplied(operation, WAIT);
      }

      Map<String, JsonNode> seenByStudent = student.board();
      Map<String, JsonNode> seenByTutor = tutor.board();
      assertThat(seenByStudent).isEqualTo(seenByTutor);
      assertThat(student.version()).isEqualTo(tutor.version()).isEqualTo(80);

      JsonNode stored =
          json.readTree((String) scenario.storedBoard(pair.sessionId()).get("content"));
      Map<String, JsonNode> inTheDatabase = new LinkedHashMap<>();
      stored.path("elements").forEach(element -> inTheDatabase.put(element.path("id").asText(), element));
      assertThat(stored.path("version").asLong()).isEqualTo(80);
      assertThat(inTheDatabase).isEqualTo(seenByStudent);
    }
  }

  /** Both participants write and erase the same ten elements, as fast as they can. */
  private static List<String> contend(BoardClient client, String who, CyclicBarrier together)
      throws Exception {
    together.await();
    List<String> operations = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      String element = "shared-" + (i % 10);
      operations.add(
          i % 4 == 3
              ? client.remove(element)
              : client.put(element, "{\"by\": \"%s\", \"step\": %d}".formatted(who, i)));
    }
    return operations;
  }

  @Test
  @DisplayName("Twenty sessions at once keep the threshold")
  void twentySessionsAtOnceKeepTheThreshold() throws Exception {
    int sessions = 20;
    List<BoardClient[]> pairs = new ArrayList<>();
    try {
      for (int s = 0; s < sessions; s++) {
        WhiteboardScenario.Pair pair = scenario.inProgress();
        BoardClient student = BoardClient.connect(port, pair.sessionId(), pair.studentToken());
        BoardClient tutor = BoardClient.connect(port, pair.sessionId(), pair.tutorToken());
        student.awaitBoard(WAIT);
        tutor.awaitBoard(WAIT);
        warmUp(student, tutor);
        pairs.add(new BoardClient[] {student, tutor});
      }

      CyclicBarrier everybody = new CyclicBarrier(sessions * 2);
      ExecutorService drawers = Executors.newFixedThreadPool(sessions * 2);
      List<Future<List<Double>>> measured = new ArrayList<>();
      for (BoardClient[] pair : pairs) {
        measured.add(drawers.submit(() -> drawAndMeasure(pair[0], pair[1], "s", everybody)));
        measured.add(drawers.submit(() -> drawAndMeasure(pair[1], pair[0], "t", everybody)));
      }
      List<Double> latencies = new ArrayList<>();
      for (Future<List<Double>> each : measured) {
        latencies.addAll(each.get(60, TimeUnit.SECONDS));
      }
      drawers.shutdown();

      report("twenty sessions", latencies);
      assertThat(latencies).hasSize(sessions * 2 * 10);
      assertThat(latencies).allSatisfy(ms -> assertThat(ms).isLessThan(SEEN_WITHIN.toMillis()));
    } finally {
      for (BoardClient[] pair : pairs) {
        pair[0].close();
        pair[1].close();
      }
    }
  }

  /**
   * Each participant sends ten operations and waits for the other to receive each one. Both of a
   * pair run at once, so each waits only for its own operations, read from what the other has.
   */
  private static List<Double> drawAndMeasure(
      BoardClient from, BoardClient to, String who, CyclicBarrier everybody) throws Exception {
    everybody.await();
    List<Double> latencies = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      long sent = System.nanoTime();
      String operation = from.draw(who + "-" + i);
      long received;
      synchronized (to) {
        received = to.awaitApplied(operation, WAIT).atNanos();
      }
      latencies.add((received - sent) / 1_000_000.0);
    }
    return latencies;
  }
}
