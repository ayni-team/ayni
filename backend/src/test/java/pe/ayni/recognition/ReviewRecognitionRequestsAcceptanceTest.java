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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.application.SessionAlerts;
import pe.ayni.recognition.application.SessionAlerts.Alert;
import pe.ayni.recognition.application.SessionRatings;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.events.RecognitionResolved;

/**
 * US29, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US29-review-recognition-requests.feature},
 * with the same names. The identity of each person, the sessions they taught and their bookings are
 * stubbed, since identity, sessions and booking are other modules; the requests are submitted through
 * the real endpoint of US28 and everything else is the real application.
 *
 * <p>Each test works in a university of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ReviewRecognitionRequestsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID coordinator = UUID.randomUUID();
  private final UUID ana = UUID.randomUUID();
  private final UUID luis = UUID.randomUUID();
  private final UUID marta = UUID.randomUUID();
  private final Map<UUID, Integer> rated = new ConcurrentHashMap<>();
  private final UUID skill = UUID.randomUUID();
  private final Map<UUID, List<SessionSummary>> taught = new ConcurrentHashMap<>();

  @Autowired private MockMvc mockMvc;
  @Autowired private RecognitionRuleRepository rules;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents applicationEvents;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private SessionsApi sessions;
  @MockitoBean private BookingApi booking;
  @MockitoBean private SessionRatings ratings;
  @MockitoBean private SessionAlerts alerts;

  private String tenant;
  private String anaRequest;
  private String luisRequest;

  /** Background: a coordinator and two students of a university of its own who submitted a request each. */
  @BeforeEach
  void aCoordinatorAndTwoStudentsWhoSubmittedARequestEach() throws Exception {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    taught.clear();
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              if (id.equals(coordinator)) {
                return new UserView(id, tenant, UserRole.COORDINATOR, "carla@upc.edu.pe", null, "Carla Rios", null, null, null);
              }
              if (id.equals(ana)) {
                return new UserView(id, tenant, UserRole.STUDENT, "ana@upc.edu.pe", "U202310949", "Ana Torres", null, null, null);
              }
              if (id.equals(luis)) {
                return new UserView(id, tenant, UserRole.STUDENT, "luis@upc.edu.pe", "U202110001", "Luis Vega", null, null, null);
              }
              if (id.equals(marta)) {
                return new UserView(id, tenant, UserRole.STUDENT, "marta@upc.edu.pe", "U202010002", "Marta Quispe", null, null, null);
              }
              throw new NoSuchElementException("user %s not found".formatted(id));
            });
    when(sessions.completedSessionsOf(any(UUID.class))).thenAnswer(call -> List.copyOf(taught.getOrDefault(call.<UUID>getArgument(0), List.of())));
    when(booking.requireBooking(any(UUID.class)))
        .thenAnswer(
            call ->
                new BookingView(call.getArgument(0), UUID.randomUUID(), UUID.randomUUID(), skill, NOW, NOW, 1, "need", BookingStatus.COMPLETED));
    rated.clear();
    when(ratings.starsOf(any()))
        .thenAnswer(
            call -> {
              Map<UUID, Integer> found = new java.util.HashMap<>();
              for (UUID id : call.<java.util.Collection<UUID>>getArgument(0)) {
                if (rated.containsKey(id)) {
                  found.put(id, rated.get(id));
                }
              }
              return found;
            });
    when(alerts.alertsOf(any())).thenReturn(Map.of());
    rules.save(new RecognitionRule(UUID.randomUUID(), tenant, 20, null, LocalDate.of(2026, 1, 1), NOW));

    anaRequest = submitFor(ana, 10, 10);
    luisRequest = submitFor(luis, 12, 8);
    jdbc.update("update recognition.requests set submitted_at = ? where id = ?", java.sql.Timestamp.from(NOW.minusSeconds(86400 * 3)), UUID.fromString(JsonPath.read(anaRequest, "$.id")));
    jdbc.update("update recognition.requests set submitted_at = ? where id = ?", java.sql.Timestamp.from(NOW.minusSeconds(86400)), UUID.fromString(JsonPath.read(luisRequest, "$.id")));
  }

  private int counter;

  private List<SessionSummary> addSessions(UUID student, int... hoursPerSession) {
    List<SessionSummary> added = new ArrayList<>();
    for (int hours : hoursPerSession) {
      Instant start = NOW.minusSeconds(86400L * (60 - counter++)).plusSeconds(100);
      SessionSummary session =
          new SessionSummary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(3600L * hours + 180), hours);
      taught.computeIfAbsent(student, id -> new ArrayList<>()).add(session);
      added.add(session);
    }
    return added;
  }

  private String submitFor(UUID student, int... hoursPerSession) throws Exception {
    addSessions(student, hoursPerSession);
    return body(
        mockMvc
            .perform(post("/api/v1/recognition/requests").header("X-Tenant-Id", tenant).header("X-User-Id", student.toString()))
            .andExpect(status().isCreated()));
  }

  private ResultActions queue(UUID user, String query) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/recognition/requests" + query).header("X-Tenant-Id", tenant).header("X-User-Id", user.toString()));
  }

  private ResultActions open(String forTenant, UUID user, String request) throws Exception {
    return mockMvc.perform(
        get("/api/v1/coordinator/recognition/requests/" + JsonPath.read(request, "$.id"))
            .header("X-Tenant-Id", forTenant)
            .header("X-User-Id", user.toString()));
  }

  private ResultActions decide(String forTenant, UUID user, String request, String json) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/recognition/requests/" + JsonPath.read(request, "$.id") + "/decision")
            .header("X-Tenant-Id", forTenant)
            .header("X-User-Id", user.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions mine(UUID student) throws Exception {
    return mockMvc.perform(get("/api/v1/recognition/requests/mine").header("X-Tenant-Id", tenant).header("X-User-Id", student.toString()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("Request queue")
  void requestQueue() throws Exception {

    UUID third = UUID.randomUUID();
    jdbc.update(
        "insert into recognition.requests (id, tenant_id, student_id, total_hours, sessions_count, status, reviewed_by, reviewed_at, decision_reason)"
            + " values (?, ?, ?, 20, 2, 'REJECTED', ?, now(), 'No')",
        third,
        tenant,
        ana,
        coordinator);

    String answer = body(queue(coordinator, "").andExpect(status().isOk()));

    assertThat(JsonPath.<List<String>>read(answer, "$.items[*].student.name")).containsExactly("Ana Torres", "Luis Vega");
    assertThat(JsonPath.<List<String>>read(answer, "$.items[*].student.studentCode")).containsExactly("U202310949", "U202110001");
    assertThat(JsonPath.<List<Integer>>read(answer, "$.items[*].totalHours")).containsExactly(20, 20);
    assertThat(JsonPath.<List<String>>read(answer, "$.items[*].submittedAt")).hasSize(2);
    assertThat(JsonPath.<List<String>>read(answer, "$.items[*].id")).doesNotContain(third.toString());
    assertThat(JsonPath.<Integer>read(answer, "$.total")).isEqualTo(2);
    String history = body(queue(coordinator, "?status=REJECTED").andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(history, "$.items[*].id")).containsExactly(third.toString());
  }

  @Test
  @DisplayName("Review with evidence")
  void reviewWithEvidence() throws Exception {

    List<SessionSummary> two = addSessions(marta, 10, 10);
    rated.put(two.get(0).sessionId(), 5);
    String martaRequest = body(mockMvc.perform(post("/api/v1/recognition/requests").header("X-Tenant-Id", tenant).header("X-User-Id", marta.toString())).andExpect(status().isCreated()));

    String answer = body(open(tenant, coordinator, martaRequest).andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(answer, "$.student.name")).isEqualTo("Marta Quispe");
    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[*].sessionId")).containsExactly(two.get(0).sessionId().toString(), two.get(1).sessionId().toString());
    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[*].startedAt")).hasSize(2).doesNotContainNull();
    assertThat(JsonPath.<List<Integer>>read(answer, "$.sessions[*].durationMinutes")).containsExactly(603, 603);
    assertThat(JsonPath.<List<Boolean>>read(answer, "$.sessions[*].presenceVerified")).containsExactly(true, true);
    assertThat(JsonPath.<Integer>read(answer, "$.sessions[0].stars")).isEqualTo(5);
    assertThat(JsonPath.<Object>read(answer, "$.sessions[1].stars")).isNull();
    assertThat(JsonPath.<Double>read(answer, "$.averageRating")).isEqualTo(5.0);
  }

  @Test
  @DisplayName("Visible audit alerts")
  void visibleAuditAlerts() throws Exception {

    UUID flagged = UUID.fromString(JsonPath.read(anaRequest, "$.sessions[1].sessionId"));
    when(alerts.alertsOf(any()))
        .thenReturn(Map.of(flagged, List.of(new Alert("SHORT_SESSIONS", "HIGH", "Sessions ended within ten minutes"))));

    String answer = body(open(tenant, coordinator, anaRequest).andExpect(status().isOk()));

    assertThat(JsonPath.<List<Object>>read(answer, "$.sessions[0].alerts")).isEmpty();
    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[1].alerts[*].kind")).containsExactly("SHORT_SESSIONS");
    assertThat(JsonPath.<List<String>>read(answer, "$.alerts[*].sessionId")).containsExactly(flagged.toString());
    assertThat(JsonPath.<List<String>>read(answer, "$.alerts[*].description")).containsExactly("Sessions ended within ten minutes");
    String clean = body(open(tenant, coordinator, luisRequest).andExpect(status().isOk()));
    assertThat(JsonPath.<List<Object>>read(clean, "$.alerts")).isEmpty();
  }

  @Test
  @DisplayName("Recorded decision")
  void recordedDecision() throws Exception {

    String approval = body(decide(tenant, coordinator, anaRequest, "{\"decision\":\"APPROVE\",\"reason\":\"The hours match the programme\"}").andExpect(status().isOk()));
    String rejection = body(decide(tenant, coordinator, luisRequest, "{\"decision\":\"REJECT\",\"reason\":\"The sessions are unrelated to the programme\"}").andExpect(status().isOk()));

    assertThat(JsonPath.<String>read(approval, "$.status")).isEqualTo("APPROVED");
    assertThat(JsonPath.<String>read(approval, "$.reviewedBy")).isEqualTo(coordinator.toString());
    assertThat(JsonPath.<String>read(rejection, "$.status")).isEqualTo("REJECTED");
    String anaSees = body(mine(ana).andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(anaSees, "$[*].status")).containsExactly("APPROVED");
    assertThat(JsonPath.<List<String>>read(anaSees, "$[*].decisionReason")).containsExactly("The hours match the programme");
    String luisSees = body(mine(luis).andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(luisSees, "$[*].status")).containsExactly("REJECTED");
    assertThat(JsonPath.<List<String>>read(luisSees, "$[*].decisionReason")).containsExactly("The sessions are unrelated to the programme");
    assertThat(applicationEvents.stream(RecognitionResolved.class).filter(event -> event.tenantId().equals(tenant)))
        .extracting(RecognitionResolved::studentId, RecognitionResolved::approved)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(ana, true), org.assertj.core.groups.Tuple.tuple(luis, false));
    String waiting = body(queue(coordinator, "").andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(waiting, "$.items[*].id")).isEmpty();
  }

  @Test
  @DisplayName("Frozen figures")
  void frozenFigures() throws Exception {

    String before = body(open(tenant, coordinator, anaRequest).andExpect(status().isOk()));
    List<SessionSummary> later = addSessions(ana, 5, 5);
    rated.put(later.get(0).sessionId(), 1);
    rated.put(UUID.fromString(JsonPath.read(anaRequest, "$.sessions[0].sessionId")), 1);

    String after = body(open(tenant, coordinator, anaRequest).andExpect(status().isOk()));

    assertThat(JsonPath.<Integer>read(after, "$.totalHours")).isEqualTo(20);
    assertThat(JsonPath.<Integer>read(after, "$.sessionsCount")).isEqualTo(2);
    assertThat(JsonPath.<List<String>>read(after, "$.sessions[*].sessionId"))
        .isEqualTo(JsonPath.<List<String>>read(before, "$.sessions[*].sessionId"));
    assertThat(JsonPath.<List<Integer>>read(after, "$.sessions[*].hours")).containsExactly(10, 10);
    assertThat(JsonPath.<Object>read(after, "$.averageRating")).isEqualTo(JsonPath.<Object>read(before, "$.averageRating"));
    assertThat(JsonPath.<List<Object>>read(after, "$.sessions[*].stars")).isEqualTo(JsonPath.<List<Object>>read(before, "$.sessions[*].stars"));
  }

  @Test
  @DisplayName("A student cannot review requests")
  void aStudentCannotReviewRequests() throws Exception {

    queue(ana, "").andExpect(status().isForbidden());
    open(tenant, ana, anaRequest).andExpect(status().isForbidden());
    decide(tenant, ana, anaRequest, "{\"decision\":\"APPROVE\",\"reason\":\"I deserve it\"}").andExpect(status().isForbidden());

    String waiting = body(queue(coordinator, "").andExpect(status().isOk()));
    assertThat(JsonPath.<Integer>read(waiting, "$.total")).isEqualTo(2);
  }

  @Test
  @DisplayName("A request of another university")
  void aRequestOfAnotherUniversity() throws Exception {

    String other = "O" + tenant;
    when(identity.requireUser(coordinator))
        .thenReturn(new UserView(coordinator, other, UserRole.COORDINATOR, "carla@u.pe", null, "Carla Rios", null, null, null));

    open(other, coordinator, anaRequest).andExpect(status().isNotFound());
    decide(other, coordinator, anaRequest, "{\"decision\":\"APPROVE\",\"reason\":\"Fine\"}").andExpect(status().isNotFound());

    assertThat(jdbc.queryForObject("select status from recognition.requests where id = ?", String.class, UUID.fromString(JsonPath.read(anaRequest, "$.id"))))
        .isEqualTo("SUBMITTED");
  }

  @Test
  @DisplayName("A decision is taken once")
  void aDecisionIsTakenOnce() throws Exception {

    decide(tenant, coordinator, anaRequest, "{\"decision\":\"APPROVE\",\"reason\":\"The hours match the programme\"}").andExpect(status().isOk());

    decide(tenant, coordinator, anaRequest, "{\"decision\":\"REJECT\",\"reason\":\"Changed my mind\"}").andExpect(status().isConflict());

    String anaSees = body(mine(ana).andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(anaSees, "$[*].status")).containsExactly("APPROVED");
    assertThat(JsonPath.<List<String>>read(anaSees, "$[*].decisionReason")).containsExactly("The hours match the programme");
  }

  @Test
  @DisplayName("A decision always says why")
  void aDecisionAlwaysSaysWhy() throws Exception {

    decide(tenant, coordinator, anaRequest, "{\"decision\":\"APPROVE\"}").andExpect(status().isBadRequest());
    decide(tenant, coordinator, anaRequest, "{\"decision\":\"APPROVE\",\"reason\":\"  \"}").andExpect(status().isBadRequest());
    decide(tenant, coordinator, anaRequest, "{\"decision\":\"MAYBE\",\"reason\":\"Unsure\"}").andExpect(status().isBadRequest());

    String waiting = body(queue(coordinator, "").andExpect(status().isOk()));
    assertThat(JsonPath.<Integer>read(waiting, "$.total")).isEqualTo(2);
  }
}
