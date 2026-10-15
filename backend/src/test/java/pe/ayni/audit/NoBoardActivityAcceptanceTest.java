package pe.ayni.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.WhiteboardClosed;

/**
 * The scenario of US47 about the whiteboard, over HTTP with real session tokens (TS03), against a
 * real PostgreSQL, with the same name as in {@code US47-anomalous-usage-alerts.feature}.
 *
 * <p>Nothing is stubbed between the session and the alert: two signed in participants end a
 * session in progress through {@code POST /api/v1/sessions/{id}/end}, sessions closes it and
 * publishes {@code WhiteboardClosed} itself, and audit hears it after the closing commits. Only
 * booking, which sessions asks for the skill of a completed session, is stubbed. The coordinator
 * reads the alert through the endpoints of US47. Each test works in a university of its own.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class NoBoardActivityAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = AuditTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @MockitoBean private BookingApi booking;

  private String tenant;
  private Person coordinator;
  private Person tutor;
  private Person student;

  /** Background: a university with an active coordinator, a tutor and a student, signed in. */
  @BeforeEach
  void aUniversityWithItsPeople() {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("B");
    coordinator = people.aCoordinator(tenant);
    tutor = people.aStudent(tenant);
    student = people.aStudent(tenant);
    when(booking.requireBooking(any(UUID.class)))
        .thenAnswer(
            call -> {
              Instant start = Instant.now().minus(Duration.ofMinutes(50));
              return new BookingView(
                  call.getArgument(0),
                  student.id(),
                  tutor.id(),
                  UUID.randomUUID(),
                  start,
                  start.plus(Duration.ofHours(1)),
                  1,
                  "Recursion before the midterm",
                  BookingStatus.CONFIRMED);
            });
  }

  @Test
  @DisplayName("No activity on the whiteboard")
  void noActivityOnTheWhiteboard() throws Exception {
    // Given a completed session in which nothing was drawn
    UUID blank = aSessionInProgress(true);
    // When the analysis completes: it runs as the session closes
    bothEnd(blank);

    assertThat(statusOf(blank)).isEqualTo("COMPLETED");
    // Then an alert about the lack of activity is generated
    assertThat(alertsOfKind("NO_BOARD_ACTIVITY")).isEqualTo(1);
    String list = coordinatorReads("/api/v1/coordinator/anomalies?status=OPEN");
    assertThat(JsonPath.<List<String>>read(list, "$.items[*].kind")).containsExactly("NO_BOARD_ACTIVITY");
    assertThat(JsonPath.<String>read(list, "$.items[0].severity")).isEqualTo("LOW");
    assertThat(JsonPath.<String>read(list, "$.items[0].subjectId")).isEqualTo(tutor.id().toString());
    String anomaly = JsonPath.read(list, "$.items[0].id");
    String detail = coordinatorReads("/api/v1/coordinator/anomalies/" + anomaly);
    assertThat(JsonPath.<List<String>>read(detail, "$.sessions[*].id")).containsExactly(blank.toString());
    assertThat(JsonPath.<List<String>>read(detail, "$.people[*].id"))
        .containsExactlyInAnyOrder(tutor.id().toString(), student.id().toString());

    // A completed session in which something was drawn raises none.
    UUID drawn = aSessionInProgress(true);
    draw(drawn);
    bothEnd(drawn);
    assertThat(statusOf(drawn)).isEqualTo("COMPLETED");
    assertThat(alertsOf(drawn)).isZero();

    // Nor does a blank session that ended unverified: its alert is the presence, not the board.
    UUID unverified = aSessionInProgress(false);
    bothEnd(unverified);
    assertThat(statusOf(unverified)).isEqualTo("UNVERIFIED");
    assertThat(alertsOf(unverified, "NO_BOARD_ACTIVITY")).isZero();
    assertThat(alertsOf(unverified, "FAILED_PRESENCE")).isEqualTo(1);

    // The same closing heard twice is still one alert.
    publish(new WhiteboardClosed(tenant, blank, tutor.id(), student.id(), false, Instant.now()));
    assertThat(alertsOfKind("NO_BOARD_ACTIVITY")).isEqualTo(1);
  }

  // Not a scenario of the story: the same closing delivered twice at the same moment.
  @Test
  @DisplayName("The same closing heard twice at once raises one alert")
  void theSameClosingHeardTwiceAtOnceRaisesOneAlert() throws Exception {
    UUID blank = aSessionInProgress(true);
    bothEnd(blank);
    jdbc.update(
        "delete from audit.anomaly_sessions where tenant_id = ? and session_id = ?", tenant, blank);
    jdbc.update("delete from audit.anomalies where tenant_id = ?", tenant);
    WhiteboardClosed closed =
        new WhiteboardClosed(tenant, blank, tutor.id(), student.id(), false, Instant.now());

    CyclicBarrier together = new CyclicBarrier(2);
    ExecutorService both = Executors.newFixedThreadPool(2);
    List<Future<?>> deliveries = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      deliveries.add(
          both.submit(
              () -> {
                together.await();
                publish(closed);
                return null;
              }));
    }
    for (Future<?> delivery : deliveries) {
      // Neither delivery fails: the one that loses finds the alert, or the primary key refuses it
      // and the listener swallows the refusal.
      delivery.get(30, TimeUnit.SECONDS);
    }
    both.shutdown();

    assertThat(alertsOfKind("NO_BOARD_ACTIVITY")).isEqualTo(1);
    assertThat(alertsOf(blank)).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  /**
   * A session between the tutor and the student that started forty minutes ago, both of them in
   * the room, written straight in its tables: how it came to be in progress is US09's business.
   * When {@code presenceConfirmed}, both confirmed their code, so closing it completes it.
   */
  private UUID aSessionInProgress(boolean presenceConfirmed) {
    UUID session = UUID.randomUUID();
    Instant start = Instant.now().minus(Duration.ofMinutes(40)).truncatedTo(ChronoUnit.SECONDS);
    jdbc.update(
        """
        insert into sessions.sessions
          (id, tenant_id, booking_id, student_id, tutor_id, scheduled_start, scheduled_end,
           room_name, started_at, status)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS')
        """,
        session,
        tenant,
        UUID.randomUUID(),
        student.id(),
        tutor.id(),
        Timestamp.from(start),
        Timestamp.from(start.plus(Duration.ofHours(1))),
        "room-" + session,
        Timestamp.from(start));
    for (Person participant : List.of(student, tutor)) {
      jdbc.update(
          """
          insert into sessions.participations (id, tenant_id, session_id, user_id, role, joined_at)
          values (?, ?, ?, ?, ?, ?)
          """,
          UUID.randomUUID(),
          tenant,
          session,
          participant.id(),
          participant.equals(student) ? "STUDENT" : "TUTOR",
          Timestamp.from(start));
      jdbc.update(
          """
          insert into sessions.presence_checks
            (id, tenant_id, session_id, user_id, code_hash, issued_at, expires_at, confirmed_at)
          values (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          UUID.randomUUID(),
          tenant,
          session,
          participant.id(),
          "0".repeat(64),
          Timestamp.from(start.plus(Duration.ofMinutes(5))),
          Timestamp.from(start.plus(Duration.ofMinutes(20))),
          presenceConfirmed ? Timestamp.from(start.plus(Duration.ofMinutes(6))) : null);
    }
    return session;
  }

  private void draw(UUID session) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/sessions/" + session + "/whiteboard")
                .header("Authorization", "Bearer " + tutor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version": 0, "elements": [{"id": "stroke-1", "type": "stroke", "data": {}}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasActivity").value(true));
  }

  private void bothEnd(UUID session) throws Exception {
    for (Person participant : List.of(student, tutor)) {
      mockMvc
          .perform(
              post("/api/v1/sessions/" + session + "/end")
                  .header("Authorization", "Bearer " + participant.token()))
          .andExpect(status().isOk());
    }
  }

  private String coordinatorReads(String path) throws Exception {
    ResultActions answer =
        mockMvc.perform(get(path).header("Authorization", "Bearer " + coordinator.token()));
    return answer.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  private void publish(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private String statusOf(UUID session) {
    return jdbc.queryForObject(
        "select status from sessions.sessions where id = ?", String.class, session);
  }

  private long alertsOfKind(String kind) {
    return jdbc.queryForObject(
        "select count(*) from audit.anomalies where tenant_id = ? and kind = ?",
        Long.class,
        tenant,
        kind);
  }

  private long alertsOf(UUID session) {
    return jdbc.queryForObject(
        "select count(*) from audit.anomaly_sessions where tenant_id = ? and session_id = ?",
        Long.class,
        tenant,
        session);
  }

  private long alertsOf(UUID session, String kind) {
    return jdbc.queryForObject(
        """
        select count(*) from audit.anomalies anomaly
        join audit.anomaly_sessions supporting on supporting.anomaly_id = anomaly.id
        where anomaly.tenant_id = ? and supporting.session_id = ? and anomaly.kind = ?
        """,
        Long.class,
        tenant,
        session,
        kind);
  }
}
