package pe.ayni.sessions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * The practice rooms, against the running backend, a real PostgreSQL and real WebSocket clients:
 * one test per scenario of {@code features/practice-rooms.feature} with the same name, but the one
 * with the switch off, which is {@link PracticeRoomsSwitchedOffTest}.
 *
 * <p>Every request carries a real session token through the real access filter. The people are
 * signed in before anything else, since signing in is not part of a room.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ayni.test.identity-headers=false",
      "ayni.sessions.practice-rooms.enabled=true"
    })
@Import(PracticeRoomsAcceptanceTest.EventsSeen.class)
class PracticeRoomsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PracticeRoomsTestDatabase.INSTANCE;

  private static final Logger log = LoggerFactory.getLogger(PracticeRoomsAcceptanceTest.class);

  private static final Duration WAIT = Duration.ofSeconds(10);

  /** Every event of the application's own classes, published from any thread. */
  @TestConfiguration
  static class EventsSeen {

    final List<String> seen = new CopyOnWriteArrayList<>();

    @Bean
    ApplicationListener<ApplicationEvent> everyEventOfTheApplication() {
      return event -> {
        Object payload =
            event instanceof PayloadApplicationEvent<?> carrying ? carrying.getPayload() : event;
        if (payload.getClass().getName().startsWith("pe.ayni.")) {
          seen.add(payload.getClass().getSimpleName());
        }
      };
    }
  }

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate http;
  @Autowired private ObjectMapper json;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @Autowired private EventsSeen events;

  private SignedInPeople people;
  private String university;
  private Person ana;
  private Person bruno;

  @BeforeEach
  void twoStudentsOfTheSameUniversityAreSignedIn() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    university = people.aUniversity("PR");
    ana = people.aStudent(university);
    bruno = people.aStudent(university);
  }

  @Test
  @DisplayName("Opening a practice room")
  void openingAPracticeRoom() throws Exception {
    Instant before = Instant.now();
    ResponseEntity<String> opened = post("/api/v1/practice-rooms", ana, null);

    assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode room = json.readTree(opened.getBody());
    UUID id = UUID.fromString(room.path("sessionId").asText());
    assertThat(id.version()).isEqualTo(4);
    assertThat(room.path("path").asText()).isEqualTo("/sesion/" + id);

    JsonNode session = ok(get("/api/v1/sessions/" + id, ana));
    assertThat(session.path("practice").asBoolean()).isTrue();
    assertThat(session.path("id").asText()).isEqualTo(id.toString());
    assertThat(session.path("catalogItem").path("name").asText()).isEqualTo("Sala de práctica");
    assertThat(session.path("needDescription").asText()).isEqualTo("Sala de práctica");
    assertThat(session.path("supportMaterial").isNull()).isTrue();
    assertThat(session.path("status").asText()).isEqualTo("SCHEDULED");
    assertThat(session.path("role").asText()).isEqualTo("TUTOR");
    Instant start = Instant.parse(session.path("scheduledStart").asText());
    Instant end = Instant.parse(session.path("scheduledEnd").asText());
    assertThat(start).isBetween(before.minusSeconds(1), Instant.now());
    assertThat(Duration.between(start, end)).isEqualTo(Duration.ofHours(4));
    assertThat(session.path("joinOpensAt").asText()).isEqualTo(session.path("scheduledStart").asText());

    assertThat(rowsNaming(id)).isZero();
  }

  @Test
  @DisplayName("The first to enter is the tutor and the second the student")
  void theFirstToEnterIsTheTutorAndTheSecondTheStudent() throws Exception {
    UUID id = aRoom();

    JsonNode first = ok(post("/api/v1/sessions/" + id + "/join", ana, null));
    assertThat(first.path("practice").asBoolean()).isTrue();
    assertThat(first.path("role").asText()).isEqualTo("TUTOR");
    assertThat(first.path("status").asText()).isEqualTo("SCHEDULED");
    assertThat(first.path("counterpart").path("name").isNull()).isTrue();

    JsonNode second = ok(post("/api/v1/sessions/" + id + "/join", bruno, null));
    assertThat(second.path("role").asText()).isEqualTo("STUDENT");
    assertThat(second.path("status").asText()).isEqualTo("IN_PROGRESS");
    assertThat(second.path("counterpart").path("name").asText()).isEqualTo(nameOf(ana));
    assertThat(second.path("startedAt").asText()).isEqualTo(second.path("studentJoinedAt").asText());

    // The video pass is the one of a real session: a token for the room's own name.
    String roomName = second.path("roomName").asText();
    assertThat(roomName).startsWith("ayni-").isEqualTo(first.path("roomName").asText());
    assertThat(second.path("video").path("url").asText()).isEqualTo("http://jitsi.test:8000/" + roomName);
    JsonNode claims =
        VideoPassToken.verifiedClaims(
            second.path("video").path("token").asText(),
            "test-only-video-secret-of-at-least-32-characters");
    assertThat(claims.path("room").asText()).isEqualTo(roomName);

    JsonNode tutorReads = ok(get("/api/v1/sessions/" + id, ana));
    assertThat(tutorReads.path("status").asText()).isEqualTo("IN_PROGRESS");
    assertThat(tutorReads.path("role").asText()).isEqualTo("TUTOR");
    assertThat(tutorReads.path("tutorId").asText()).isEqualTo(ana.id().toString());
    assertThat(tutorReads.path("studentId").asText()).isEqualTo(bruno.id().toString());
    assertThat(tutorReads.path("counterpart").path("name").asText()).isEqualTo(nameOf(bruno));
    assertThat(tutorReads.path("presence").path("attemptsLeft").asInt()).isEqualTo(5);

    // Joining again changes nothing.
    JsonNode again = ok(post("/api/v1/sessions/" + id + "/join", ana, null));
    assertThat(again.path("role").asText()).isEqualTo("TUTOR");
    assertThat(again.path("tutorJoinedAt").asText()).isEqualTo(first.path("tutorJoinedAt").asText());
  }

  @Test
  @DisplayName("A third person cannot enter a practice room")
  void aThirdPersonCannotEnterAPracticeRoom() throws Exception {
    UUID id = aRoomBothEntered();
    Person carla = people.aStudent(university);

    assertThat(post("/api/v1/sessions/" + id + "/join", carla, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get("/api/v1/sessions/" + id, carla).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(get("/api/v1/sessions/" + id + "/whiteboard", carla).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThatThrownBy(() -> BoardClient.connect(port, id, carla.token()))
        .hasStackTraceContaining("403");

    // The two who are in keep it.
    assertThat(ok(get("/api/v1/sessions/" + id, bruno)).path("role").asText()).isEqualTo("STUDENT");
  }

  @Test
  @DisplayName("Somebody who did not enter a full room cannot read it")
  void somebodyWhoDidNotEnterAFullRoomCannotReadIt() throws Exception {
    UUID id = aRoomBothEntered();
    Person dana = people.aStudent(university);

    ResponseEntity<String> read = get("/api/v1/sessions/" + id, dana);
    assertThat(read.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(json.readTree(read.getBody()).path("message").asText())
        .isEqualTo("Only the student and the tutor of this session can see it or join it");
    assertThat(get("/api/v1/sessions/" + id + "/whiteboard", dana).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(post("/api/v1/sessions/" + id + "/presence", dana, "{\"code\": \"123456\"}").getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(post("/api/v1/sessions/" + id + "/end", dana, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    // Somebody of another university does not find it, as with a session of another university.
    Person elsewhere = people.aStudent(people.aUniversity("PX"));
    assertThat(get("/api/v1/sessions/" + id, elsewhere).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("What one draws the other receives and the board reads the same")
  void whatOneDrawsTheOtherReceivesAndTheBoardReadsTheSame() throws Exception {
    UUID id = aRoomBothEntered();
    try (BoardClient tutor = BoardClient.connect(port, id, ana.token());
        BoardClient student = BoardClient.connect(port, id, bruno.token())) {
      assertThat(tutor.awaitBoard(WAIT).message().path("version").asLong()).isZero();
      student.awaitBoard(WAIT);

      String byTutor = tutor.draw("tutor-stroke");
      student.awaitApplied(byTutor, WAIT);
      String byStudent = student.draw("student-stroke");
      tutor.awaitApplied(byStudent, WAIT);
      tutor.awaitApplied(byTutor, WAIT);
      student.awaitApplied(byStudent, WAIT);

      assertThat(tutor.board()).isEqualTo(student.board());
      assertThat(tutor.board().keySet()).containsExactly("tutor-stroke", "student-stroke");
      assertThat(student.version()).isEqualTo(2);

      JsonNode board = ok(get("/api/v1/sessions/" + id + "/whiteboard", bruno));
      assertThat(board.path("version").asLong()).isEqualTo(2);
      assertThat(board.path("hasActivity").asBoolean()).isTrue();
      assertThat(board.path("live").asBoolean()).isTrue();
      List<String> ids = new ArrayList<>();
      board.path("elements").forEach(element -> ids.add(element.path("id").asText()));
      assertThat(ids).containsExactly("tutor-stroke", "student-stroke");

      // A refused operation answers an error and the channel stays open, as on any board.
      tutor.send("{\"type\": \"operation\", \"operation\": {\"id\": \"x\", \"action\": \"PUT_ELEMENT\"}}");
      assertThat(tutor.awaitError(WAIT).message().path("status").asInt()).isEqualTo(400);

      student.close();
      tutor.awaitApplied(tutor.draw("while-away"), WAIT);
      try (BoardClient back = BoardClient.connect(port, id, bruno.token())) {
        JsonNode whole = back.awaitBoard(WAIT).message();
        assertThat(whole.path("version").asLong()).isEqualTo(3);
        assertThat(whole.path("elements")).hasSize(3);
      }

      // Replacing it needs the version last read, as on any board.
      String replacement =
          "{\"version\": %d, \"elements\": [{\"id\": \"only\", \"type\": \"stroke\", \"data\": {}}]}";
      assertThat(put("/api/v1/sessions/" + id + "/whiteboard", ana, replacement.formatted(1))
              .getStatusCode())
          .isEqualTo(HttpStatus.CONFLICT);
      JsonNode replaced = ok(put("/api/v1/sessions/" + id + "/whiteboard", ana, replacement.formatted(3)));
      assertThat(replaced.path("version").asLong()).isEqualTo(4);
      assertThat(replaced.path("elements")).hasSize(1);
    }
  }

  @Test
  @DisplayName("Ending it by both closes the room")
  void endingItByBothClosesTheRoom() throws Exception {
    UUID id = aRoomBothEntered();
    try (BoardClient tutor = BoardClient.connect(port, id, ana.token());
        BoardClient student = BoardClient.connect(port, id, bruno.token())) {
      tutor.awaitBoard(WAIT);
      student.awaitBoard(WAIT);
      student.awaitApplied(tutor.draw("a-stroke"), WAIT);

      JsonNode first = ok(post("/api/v1/sessions/" + id + "/end", ana, null));
      assertThat(first.path("status").asText()).isEqualTo("IN_PROGRESS");
      assertThat(first.path("endConfirmedAt").isNull()).isFalse();
      assertThat(first.path("endedAt").isNull()).isTrue();
      assertThat(ok(get("/api/v1/sessions/" + id, ana)).path("endConfirmedAt").isNull()).isFalse();
      assertThat(tutor.isOpen()).isTrue();

      JsonNode second = ok(post("/api/v1/sessions/" + id + "/end", bruno, null));
      assertThat(second.path("status").asText()).isEqualTo("COMPLETED");
      assertThat(second.path("endedAt").isNull()).isFalse();

      assertThat(tutor.awaitClosed(WAIT).getCode()).isEqualTo(4409);
      assertThat(student.awaitClosed(WAIT).getCode()).isEqualTo(4409);
    }
    assertThat(get("/api/v1/sessions/" + id, ana).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(post("/api/v1/sessions/" + id + "/join", bruno, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/api/v1/sessions/" + id + "/whiteboard", ana).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    // Nothing was credited: the university has no movement at all.
    assertThat(count("select count(*) from wallet.ledger_entries where tenant_id = '" + university + "'"))
        .isZero();
  }

  @Test
  @DisplayName("An unknown id answers 404 as before")
  void anUnknownIdAnswers404AsBefore() throws Exception {
    aRoom();
    UUID unknown = UUID.randomUUID();

    ResponseEntity<String> read = get("/api/v1/sessions/" + unknown, ana);
    assertThat(read.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(json.readTree(read.getBody()).path("message").asText())
        .isEqualTo("Session not found: " + unknown);
    assertThat(post("/api/v1/sessions/" + unknown + "/join", ana, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get("/api/v1/sessions/" + unknown + "/whiteboard", ana).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(post("/api/v1/sessions/" + unknown + "/end", ana, null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThatThrownBy(() -> BoardClient.connect(port, unknown, ana.token()))
        .hasStackTraceContaining("404");
  }

  @Test
  @DisplayName("A whole practice leaves every table as it was")
  void aWholePracticeLeavesEveryTableAsItWas() throws Exception {
    Person coordinator = people.aCoordinator(university);
    Map<String, String> before = everyTable();
    int eventsBefore = events.seen.size();

    UUID id = UUID.fromString(json.readTree(post("/api/v1/practice-rooms", ana, null).getBody())
        .path("sessionId").asText());
    ok(get("/api/v1/sessions/" + id, ana));
    ok(post("/api/v1/sessions/" + id + "/join", ana, null));
    ok(get("/api/v1/sessions/" + id, bruno));
    ok(post("/api/v1/sessions/" + id + "/join", bruno, null));
    try (BoardClient tutor = BoardClient.connect(port, id, ana.token());
        BoardClient student = BoardClient.connect(port, id, bruno.token())) {
      tutor.awaitBoard(WAIT);
      student.awaitBoard(WAIT);
      student.awaitApplied(tutor.draw("tutor-stroke"), WAIT);
      tutor.awaitApplied(student.draw("student-stroke"), WAIT);
      ok(get("/api/v1/sessions/" + id + "/whiteboard", ana));

      // No code is sent, any six digits confirm it; a resend answers as it would and sends nothing.
      assertThat(post("/api/v1/sessions/" + id + "/presence/resend", bruno, null).getStatusCode())
          .isEqualTo(HttpStatus.ACCEPTED);
      assertThat(post("/api/v1/sessions/" + id + "/presence", ana, "{\"code\": \"12345\"}").getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
      ok(post("/api/v1/sessions/" + id + "/presence", ana, "{\"code\": \"123456\"}"));
      ok(post("/api/v1/sessions/" + id + "/presence", bruno, "{\"code\": \"999999\"}"));
      assertThat(ok(get("/api/v1/sessions/" + id, bruno)).path("presence").path("confirmedAt").isNull())
          .isFalse();

      ok(post("/api/v1/sessions/" + id + "/end", ana, null));
      assertThat(ok(post("/api/v1/sessions/" + id + "/end", bruno, null)).path("status").asText())
          .isEqualTo("COMPLETED");
      tutor.awaitClosed(WAIT);
    }
    assertThat(get("/api/v1/sessions/" + id, ana).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    Map<String, String> after = everyTable();
    log.info("A practice left {} tables of every schema as they were", after.size());
    assertThat(after).hasSizeGreaterThan(50).isEqualTo(before);
    assertThat(after).containsKey("public.event_publication");
    assertThat(events.seen.subList(eventsBefore, events.seen.size())).isEmpty();

    JsonNode ledger = ok(get("/api/v1/coordinator/ledger/integrity", coordinator));
    assertThat(ledger.path("intact").asBoolean()).isTrue();
    JsonNode auditLog = ok(get("/api/v1/coordinator/audit-log/integrity", coordinator));
    assertThat(auditLog.path("intact").asBoolean()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------

  private UUID aRoom() throws Exception {
    return UUID.fromString(
        json.readTree(post("/api/v1/practice-rooms", ana, null).getBody()).path("sessionId").asText());
  }

  private UUID aRoomBothEntered() throws Exception {
    UUID id = aRoom();
    ok(post("/api/v1/sessions/" + id + "/join", ana, null));
    ok(post("/api/v1/sessions/" + id + "/join", bruno, null));
    return id;
  }

  /** The name SignedInPeople gives a student. */
  private static String nameOf(Person student) {
    return "Student " + student.id().toString().substring(0, 4);
  }

  private JsonNode ok(ResponseEntity<String> response) throws Exception {
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("%s: %s", response.getStatusCode(), response.getBody())
        .isTrue();
    return response.getBody() == null ? json.nullNode() : json.readTree(response.getBody());
  }

  private ResponseEntity<String> get(String path, Person person) {
    return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(person)), String.class);
  }

  private ResponseEntity<String> post(String path, Person person, String body) {
    return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(person)), String.class);
  }

  private ResponseEntity<String> put(String path, Person person, String body) {
    return http.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers(person)), String.class);
  }

  private static HttpHeaders headers(Person person) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(person.token());
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  /** Rows of the sessions' tables that name this id, read as the owner of the tables. */
  private long rowsNaming(UUID id) throws SQLException {
    return count("select count(*) from sessions.sessions where id = '" + id + "'")
        + count("select count(*) from sessions.whiteboards where session_id = '" + id + "'")
        + count("select count(*) from sessions.participations where session_id = '" + id + "'");
  }

  private static long count(String sql) throws SQLException {
    try (Connection owner = owner();
        Statement statement = owner.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  /**
   * Every table of every schema, with how many rows it has and a fingerprint of all of them, read
   * as the owner, which row level security does not filter.
   */
  private static Map<String, String> everyTable() throws SQLException {
    Map<String, String> tables = new TreeMap<>();
    try (Connection owner = owner(); Statement statement = owner.createStatement()) {
      List<String> names = new ArrayList<>();
      try (ResultSet result =
          statement.executeQuery(
              "select quote_ident(table_schema) || '.' || quote_ident(table_name)"
                  + " from information_schema.tables where table_type = 'BASE TABLE'"
                  + " and table_schema not in ('pg_catalog', 'information_schema')")) {
        while (result.next()) {
          names.add(result.getString(1));
        }
      }
      for (String name : names) {
        try (ResultSet result =
            statement.executeQuery(
                "select count(*), coalesce(md5(string_agg(t::text, '|' order by t::text)), '')"
                    + " from " + name + " t")) {
          result.next();
          tables.put(name, result.getLong(1) + " rows, " + result.getString(2));
        }
      }
    }
    return tables;
  }

  private static Connection owner() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
