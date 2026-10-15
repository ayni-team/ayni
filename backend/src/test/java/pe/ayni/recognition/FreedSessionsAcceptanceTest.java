package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;

/**
 * What a rejection does to the sessions of a request (US29), over HTTP against a real PostgreSQL
 * with real session tokens (TS03). One test per scenario of {@code
 * features/US29-review-recognition-requests.feature} that names this class, with the same name.
 *
 * <p>The student and the coordinator are real people of a university of their own, signed in; the
 * sessions the student taught and their bookings are stubbed, since sessions and booking are other
 * modules. The requests are submitted and decided through the real endpoints.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class FreedSessionsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant TAUGHT = Instant.parse("2026-09-21T14:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RecognitionRuleRepository rules;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @MockitoBean private SessionsApi sessions;
  @MockitoBean private BookingApi booking;

  private String tenant;
  private Person coordinator;
  private Person student;
  private final List<SessionSummary> taught = new ArrayList<>();

  /** A university that asks for ten hours, its coordinator, and a student who taught ten. */
  @BeforeEach
  void aStudentWhoTaughtTheHoursOfTheRule() {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("R");
    coordinator = people.aCoordinator(tenant);
    student = people.aStudent(tenant);
    rules.save(new RecognitionRule(UUID.randomUUID(), tenant, 10, null, LocalDate.of(2026, 1, 1), Instant.now()));
    taught.clear();
    taught.add(session(4, 0));
    taught.add(session(6, 1));
    when(sessions.completedSessionsOf(student.id())).thenAnswer(call -> List.copyOf(taught));
    when(booking.requireBooking(any(UUID.class)))
        .thenAnswer(
            call ->
                new BookingView(
                    call.getArgument(0), UUID.randomUUID(), student.id(), UUID.randomUUID(), TAUGHT, TAUGHT, 1,
                    "need", BookingStatus.CONFIRMED));
  }

  private static SessionSummary session(int hours, int day) {
    Instant start = TAUGHT.plusSeconds(86400L * day);
    return new SessionSummary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(3600L * hours), hours);
  }

  private org.springframework.test.web.servlet.ResultActions submit() throws Exception {
    return mockMvc.perform(
        post("/api/v1/recognition/requests").header("Authorization", "Bearer " + student.token()));
  }

  private String submitted() throws Exception {
    return submit().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  }

  private void decide(String requestId, String decision) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/coordinator/recognition/requests/" + requestId + "/decision")
                .header("Authorization", "Bearer " + coordinator.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"decision": "%s", "reason": "Decided after reading the sessions"}
                    """
                        .formatted(decision)))
        .andExpect(status().isOk());
  }

  private String caseOf(String requestId) throws Exception {
    return mockMvc
        .perform(
            get("/api/v1/coordinator/recognition/requests/" + requestId)
                .header("Authorization", "Bearer " + coordinator.token()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private List<String> sessionsOf(String body) {
    return JsonPath.read(body, "$.sessions[*].sessionId");
  }

  @Test
  @DisplayName("A rejected request gives its sessions back")
  void aRejectedRequestGivesItsSessionsBack() throws Exception {
    String first = submitted();
    String rejected = JsonPath.read(first, "$.id");
    List<String> presented = sessionsOf(first);
    assertThat(presented)
        .containsExactlyInAnyOrder(taught.get(0).sessionId().toString(), taught.get(1).sessionId().toString());
    // While it waits, the sessions are taken.
    submit().andExpect(status().isConflict());

    decide(rejected, "REJECT");

    String second = submitted();
    String approved = JsonPath.read(second, "$.id");
    assertThat(approved).isNotEqualTo(rejected);
    assertThat(sessionsOf(second)).containsExactlyInAnyOrderElementsOf(presented);
    assertThat(JsonPath.<Integer>read(second, "$.totalHours")).isEqualTo(10);

    // The rejected file is still what the student presented.
    String file = caseOf(rejected);
    assertThat(JsonPath.<String>read(file, "$.status")).isEqualTo("REJECTED");
    assertThat(JsonPath.<Integer>read(file, "$.totalHours")).isEqualTo(10);
    assertThat(JsonPath.<Integer>read(file, "$.sessionsCount")).isEqualTo(2);
    assertThat(sessionsOf(file)).containsExactlyInAnyOrderElementsOf(presented);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from recognition.request_sessions where request_id = ?::uuid and released_at is not null",
                Long.class,
                rejected))
        .isEqualTo(2);

    // An approved request keeps its sessions for good.
    decide(approved, "APPROVE");
    String refused = submit().andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
    assertThat(JsonPath.<Integer>read(refused, "$.missingHours")).isEqualTo(10);
  }

  @Test
  @DisplayName("Two requests at once after a rejection take the freed sessions once")
  void twoRequestsAtOnceAfterARejectionTakeTheFreedSessionsOnce() throws Exception {
    decide(JsonPath.read(submitted(), "$.id"), "REJECT");

    CyclicBarrier together = new CyclicBarrier(2);
    ExecutorService both = Executors.newFixedThreadPool(2);
    List<Future<Integer>> answers = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      answers.add(
          both.submit(
              () -> {
                together.await();
                return submit().andReturn().getResponse().getStatus();
              }));
    }
    List<Integer> statuses = new ArrayList<>();
    for (Future<Integer> answer : answers) {
      statuses.add(answer.get(30, TimeUnit.SECONDS));
    }
    both.shutdown();

    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from recognition.request_sessions where tenant_id = ? and released_at is null",
                Long.class,
                tenant))
        .isEqualTo(2);
  }
}
