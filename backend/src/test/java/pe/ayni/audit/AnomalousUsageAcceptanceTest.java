package pe.ayni.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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
import pe.ayni.audit.application.DetectRepeatedPairsUseCase;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.sessions.SessionStatus;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionView;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.SessionCompleted;
import pe.ayni.shared.events.SessionUnverified;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US47, over HTTP, against a real database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US47-anomalous-usage-alerts.feature},
 * with the same names. Every alert is produced by publishing the real events inside a transaction,
 * as the module that owns them would; the one that a job finds is asked for by calling the use case
 * the job calls, which is what the tests of the other jobs do. Identity and sessions are other
 * modules, so what they would say is stubbed. Each test works in a university of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnomalousUsageAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = AuditTestDatabase.INSTANCE;

  private final UUID coordinator = UUID.randomUUID();
  private final UUID otherCoordinator = UUID.randomUUID();
  private final UUID restricted = UUID.randomUUID();
  private final UUID tutor = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();
  private final UUID outsider = UUID.randomUUID();
  private final UUID item = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @Autowired private DetectRepeatedPairsUseCase repeatedPairs;
  @Autowired private AuditApi auditApi;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private SessionsApi sessions;

  private String tenant;

  /** Background: a university with its coordinators, a tutor and a student. */
  @BeforeEach
  void aUniversityWithItsPeople() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              if (id.equals(coordinator) || id.equals(otherCoordinator) || id.equals(restricted)) {
                return new UserView(id, tenant, UserRole.COORDINATOR, "carla@upc.edu.pe", null, "Carla Rios", null, null, null);
              }
              if (id.equals(tutor)) {
                return new UserView(id, tenant, UserRole.STUDENT, "bruno@upc.edu.pe", "U2021", "Bruno Salas", null, null, null);
              }
              if (id.equals(student)) {
                return new UserView(id, tenant, UserRole.STUDENT, "ana@upc.edu.pe", "U2023", "Ana Paredes", null, null, null);
              }
              throw new NoSuchElementException("user %s not found".formatted(id));
            });
    when(identity.isActive(any(UUID.class))).thenAnswer(call -> !call.getArgument(0).equals(restricted));
    when(sessions.requireSession(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              Instant start = Instant.parse("2026-10-05T15:00:00Z");
              return new SessionView(id, UUID.randomUUID(), student, tutor, start, start.plusSeconds(3600), SessionStatus.COMPLETED);
            });
  }

  // ---------------------------------------------------------------------------------------------
  // The scenarios of the story
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("Alert generated")
  void alertGenerated() throws Exception {
    List<UUID> together = completedSessions(tutor, student, 4, 1);
    // A pair that stays within what is usual, and one whose four sessions are too old to count.
    UUID otherStudent = UUID.randomUUID();
    completedSessions(tutor, otherStudent, 3, 1);
    UUID oldStudent = UUID.randomUUID();
    completedSessions(tutor, oldStudent, 4, 40);
    // The same pair in another university does not add up to this one.
    String other = "O" + UUID.randomUUID().toString().substring(0, 8);
    publishCompleted(other, UUID.randomUUID(), tutor, student, Instant.now().minus(Duration.ofDays(2)));

    assertThat(analyse()).isEqualTo(1);

    String alerts = alerts("");
    assertThat(JsonPath.<Integer>read(alerts, "$.total")).isEqualTo(1);
    assertThat(JsonPath.<String>read(alerts, "$.items[0].kind")).isEqualTo("REPEATED_PAIR");
    assertThat(JsonPath.<String>read(alerts, "$.items[0].status")).isEqualTo("OPEN");
    assertThat(JsonPath.<String>read(alerts, "$.items[0].subjectId")).isEqualTo(tutor.toString());
    assertThat(JsonPath.<Integer>read(alerts, "$.items[0].sessionCount")).isEqualTo(4);

    // It names the people and the sessions, in the table.
    UUID anomaly = UUID.fromString(JsonPath.read(alerts, "$.items[0].id"));
    Map<String, Object> row = jdbc.queryForMap("select * from audit.anomalies where id = ?", anomaly);
    assertThat(row.get("tenant_id")).isEqualTo(tenant);
    assertThat(row.get("subject_id").toString()).isEqualTo(tutor.toString());
    assertThat(row.get("detail").toString()).contains(student.toString());
    assertThat(supportingSessionsOf(anomaly)).containsExactlyInAnyOrderElementsOf(together);

    // Looking again, now or at the next run of the job, finds it already raised.
    assertThat(analyse()).isZero();
    assertThat(JsonPath.<Integer>read(alerts(""), "$.total")).isEqualTo(1);
  }

  @Test
  @DisplayName("Support of the alert")
  void supportOfTheAlert() throws Exception {
    List<UUID> together = completedSessions(tutor, student, 5, 1);
    analyse();
    UUID anomaly = onlyAnomaly();

    String detail = open(tenant, coordinator, anomaly).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    assertThat(JsonPath.<String>read(detail, "$.kind")).isEqualTo("REPEATED_PAIR");
    assertThat(JsonPath.<String>read(detail, "$.severity")).isEqualTo("MEDIUM");
    assertThat(JsonPath.<String>read(detail, "$.status")).isEqualTo("OPEN");
    assertThat(JsonPath.<String>read(detail, "$.pattern.sessions")).isEqualTo("5");
    assertThat(JsonPath.<String>read(detail, "$.pattern.threshold")).isEqualTo("3");
    assertThat(JsonPath.<String>read(detail, "$.pattern.periodDays")).isEqualTo("30");
    assertThat(JsonPath.<String>read(detail, "$.description")).contains("5 sessions");
    assertThat(JsonPath.<List<String>>read(detail, "$.people[*].id")).containsExactlyInAnyOrder(tutor.toString(), student.toString());
    assertThat(JsonPath.<List<String>>read(detail, "$.people[?(@.role=='TUTOR')].fullName")).containsExactly("Bruno Salas");
    assertThat(JsonPath.<List<String>>read(detail, "$.people[?(@.role=='STUDENT')].fullName")).containsExactly("Ana Paredes");
    assertThat(JsonPath.<List<String>>read(detail, "$.sessions[*].id")).containsExactlyInAnyOrderElementsOf(together.stream().map(UUID::toString).toList());
    assertThat(JsonPath.<String>read(detail, "$.sessions[0].status")).isEqualTo("COMPLETED");
    assertThat(JsonPath.<String>read(detail, "$.sessions[0].scheduledStart")).isEqualTo("2026-10-05T15:00:00Z");
    assertThat(detail).doesNotContain("@upc.edu.pe");

    // Another university's alert is not found, never refused.
    String other = "O" + UUID.randomUUID().toString().substring(0, 8);
    open(other, coordinator, anomaly).andExpect(status().isNotFound());
    open(tenant, coordinator, UUID.randomUUID()).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Dismissing an alert")
  void dismissingAnAlert() throws Exception {
    UUID session = UUID.randomUUID();
    publishUnverified(session, List.of(student));
    UUID anomaly = onlyAnomaly();
    assertThat(openAlertsOf(session)).hasSize(1);

    // A reason is what a dismissal asks for.
    dismiss(tenant, coordinator, anomaly, "{}").andExpect(status().isBadRequest());
    dismiss(tenant, coordinator, anomaly, "{\"reason\": \"   \"}").andExpect(status().isBadRequest());
    assertThat(statusOf(anomaly)).isEqualTo("OPEN");

    String answer =
        dismiss(tenant, coordinator, anomaly, "{\"reason\": \"The student had a connection problem\"}")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JsonPath.<String>read(answer, "$.status")).isEqualTo("DISMISSED");
    assertThat(JsonPath.<String>read(answer, "$.resolvedBy")).isEqualTo(coordinator.toString());
    assertThat(JsonPath.<String>read(answer, "$.reason")).isEqualTo("The student had a connection problem");
    Map<String, Object> row = jdbc.queryForMap("select * from audit.anomalies where id = ?", anomaly);
    assertThat(row.get("status")).isEqualTo("DISMISSED");
    assertThat(row.get("resolved_by").toString()).isEqualTo(coordinator.toString());
    assertThat(row.get("resolved_at")).isNotNull();
    assertThat(row.get("resolution_reason")).isEqualTo("The student had a connection problem");

    // The decision is one more entry of the log, without the free text of the reason.
    Map<String, Object> entry =
        jdbc.queryForMap(
            "select * from audit.activity_log where tenant_id = ? and action = 'ANOMALY_DISMISSED'", tenant);
    assertThat(entry.get("actor_id").toString()).isEqualTo(coordinator.toString());
    assertThat(entry.get("actor_role")).isEqualTo("COORDINATOR");
    assertThat(entry.get("resource_type")).isEqualTo("ANOMALY");
    assertThat(entry.get("resource_id").toString()).isEqualTo(anomaly.toString());
    assertThat(entry.get("metadata").toString()).doesNotContain("connection problem");
    String log =
        mockMvc
            .perform(get("/api/v1/coordinator/audit-log?resourceType=ANOMALY").header("X-Tenant-Id", tenant).header("X-User-Id", coordinator.toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<String>>read(log, "$.items[*].action")).containsExactly("ANOMALY_DISMISSED");

    // It leaves the open ones, and it is listed with the dismissed.
    assertThat(openAlertsOf(session)).isEmpty();
    assertThat(JsonPath.<Integer>read(alerts("?status=OPEN"), "$.total")).isZero();
    assertThat(JsonPath.<List<String>>read(alerts("?status=dismissed"), "$.items[*].id")).containsExactly(anomaly.toString());
    assertThat(JsonPath.<String>read(open(tenant, coordinator, anomaly).andReturn().getResponse().getContentAsString(), "$.resolution.reason"))
        .isEqualTo("The student had a connection problem");

    // Decided once: dismissing it again changes nothing and writes nothing.
    dismiss(tenant, otherCoordinator, anomaly, "{\"reason\": \"Looked at it again\"}").andExpect(status().isConflict());
    assertThat(jdbc.queryForObject("select resolved_by from audit.anomalies where id = ?", UUID.class, anomaly)).isEqualTo(coordinator);
    assertThat(jdbc.queryForObject("select count(*) from audit.activity_log where tenant_id = ? and action = 'ANOMALY_DISMISSED'", Long.class, tenant)).isEqualTo(1);

    // Another university's alert is not found.
    UUID another = UUID.randomUUID();
    publishUnverified(another, List.of(student));
    String other = "O" + UUID.randomUUID().toString().substring(0, 8);
    UUID foreign = anomalyOf(another);
    dismiss(other, coordinator, foreign, "{\"reason\": \"Not mine\"}").andExpect(status().isNotFound());
    assertThat(statusOf(foreign)).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("The platform does not stop for the analysis")
  void thePlatformDoesNotStopForTheAnalysis() {
    when(sessions.completedSessionsOf(any())).thenThrow(new IllegalStateException("sessions is unavailable"));
    UUID session = UUID.randomUUID();
    UUID marker = UUID.randomUUID();

    // The publisher writes something of its own and then announces it: both are committed, and
    // nothing the analysis does wrong comes back to it.
    assertThatCode(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      jdbc.update(
                          "insert into audit.activity_log (id, tenant_id, actor_role, action, resource_type, resource_id)"
                              + " values (?, ?, 'SYSTEM', 'PUBLISHER_WORK', 'SESSION', ?)",
                          marker,
                          tenant,
                          session);
                      events.publishEvent(completedEvent(tenant, session, tutor, student, Instant.now()));
                    }))
        .doesNotThrowAnyException();

    assertThat(rows("action = 'PUBLISHER_WORK' and resource_id = ?", session)).isEqualTo(1);
    assertThat(rows("action = 'SESSION_COMPLETED' and resource_id = ?", session)).isEqualTo(1);
    assertThat(anomalies("")).isZero();

    // A publisher that rolls back published nothing: nothing is analysed either.
    UUID undone = UUID.randomUUID();
    transactions.executeWithoutResult(
        status -> {
          events.publishEvent(unverifiedEvent(undone, List.of(student)));
          status.setRollbackOnly();
        });
    assertThat(anomalies("")).isZero();
    assertThat(rows("resource_id = ?", undone)).isZero();

    // And when the analysis works again it goes on with what comes next.
    UUID next = UUID.randomUUID();
    publishUnverified(next, List.of(student));
    assertThat(anomalies("and kind = 'FAILED_PRESENCE'")).isEqualTo(1);
  }

  @Test
  @DisplayName("Abnormally short session")
  void abnormallyShortSession() throws Exception {
    Instant start = Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.SECONDS);
    UUID shortOne = completedWithDuration(start, Duration.ofMinutes(20));
    UUID longEnough = completedWithDuration(start, Duration.ofMinutes(50));
    UUID exactlyHalf = completedWithDuration(start, Duration.ofMinutes(30));
    UUID noRecord = UUID.randomUUID();
    publishCompleted(tenant, noRecord, tutor, student, Instant.now());

    assertThat(anomalies("")).isEqualTo(1);
    UUID anomaly = onlyAnomaly();
    Map<String, Object> row = jdbc.queryForMap("select * from audit.anomalies where id = ?", anomaly);
    assertThat(row.get("kind")).isEqualTo("SHORT_DURATION");
    assertThat(row.get("subject_id").toString()).isEqualTo(tutor.toString());
    assertThat(row.get("severity")).isEqualTo("MEDIUM");
    assertThat(supportingSessionsOf(anomaly)).containsExactly(shortOne);
    String detail = open(tenant, coordinator, anomaly).andReturn().getResponse().getContentAsString();
    assertThat(JsonPath.<String>read(detail, "$.pattern.bookedMinutes")).isEqualTo("60");
    assertThat(JsonPath.<String>read(detail, "$.pattern.actualMinutes")).isEqualTo("20");
    assertThat(JsonPath.<List<String>>read(detail, "$.people[*].id")).containsExactlyInAnyOrder(tutor.toString(), student.toString());
    assertThat(openAlertsOf(shortOne)).hasSize(1);
    assertThat(openAlertsOf(longEnough)).isEmpty();
    assertThat(openAlertsOf(exactlyHalf)).isEmpty();

    // The same event delivered twice is one alert.
    publishCompleted(tenant, shortOne, tutor, student, Instant.now());
    assertThat(anomalies("")).isEqualTo(1);

    // A session that did not even reach a quarter of the hour weighs more.
    UUID tiny = completedWithDuration(start, Duration.ofMinutes(5));
    assertThat(jdbc.queryForObject("select severity from audit.anomalies where id = ?", String.class, anomalyOf(tiny))).isEqualTo("HIGH");
  }

  @Test
  @DisplayName("Failed presence verification")
  void failedPresenceVerification() throws Exception {
    UUID session = UUID.randomUUID();

    publishUnverified(session, List.of(student));

    assertThat(anomalies("")).isEqualTo(1);
    UUID anomaly = onlyAnomaly();
    Map<String, Object> row = jdbc.queryForMap("select * from audit.anomalies where id = ?", anomaly);
    assertThat(row.get("kind")).isEqualTo("FAILED_PRESENCE");
    assertThat(row.get("status")).isEqualTo("OPEN");
    assertThat(row.get("severity")).isEqualTo("MEDIUM");
    assertThat(row.get("subject_id").toString()).isEqualTo(tutor.toString());
    assertThat(row.get("detail").toString()).contains(student.toString());
    assertThat(supportingSessionsOf(anomaly)).containsExactly(session);
    String detail = open(tenant, coordinator, anomaly).andReturn().getResponse().getContentAsString();
    assertThat(JsonPath.<String>read(detail, "$.pattern.unconfirmedUserIds")).isEqualTo(student.toString());
    assertThat(JsonPath.<List<String>>read(detail, "$.people[*].id")).containsExactlyInAnyOrder(tutor.toString(), student.toString());
    assertThat(JsonPath.<List<String>>read(detail, "$.sessions[*].id")).containsExactly(session.toString());

    // The same event delivered twice is one alert.
    publishUnverified(session, List.of(student));
    assertThat(anomalies("")).isEqualTo(1);

    // When nobody confirmed, it weighs more.
    UUID nobody = UUID.randomUUID();
    publishUnverified(nobody, List.of(tutor, student));
    assertThat(jdbc.queryForObject("select severity from audit.anomalies where id = ?", String.class, anomalyOf(nobody))).isEqualTo("HIGH");
  }

  // ---------------------------------------------------------------------------------------------
  // What the story does not name
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("Only a coordinator of the university reaches the alerts")
  void onlyACoordinatorOfTheUniversityReachesTheAlerts() throws Exception {
    UUID session = UUID.randomUUID();
    publishUnverified(session, List.of(student));
    UUID anomaly = onlyAnomaly();

    for (UUID person : List.of(student, restricted, outsider)) {
      mockMvc.perform(get("/api/v1/coordinator/anomalies").header("X-Tenant-Id", tenant).header("X-User-Id", person.toString())).andExpect(status().isForbidden());
      open(tenant, person, anomaly).andExpect(status().isForbidden());
      dismiss(tenant, person, anomaly, "{\"reason\": \"Because\"}").andExpect(status().isForbidden());
    }
    String answer = open(tenant, student, anomaly).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
    assertThat(JsonPath.<Integer>read(answer, "$.status")).isEqualTo(403);
    assertThat(statusOf(anomaly)).isEqualTo("OPEN");

    // A malformed request is not a refusal.
    mockMvc.perform(get("/api/v1/coordinator/anomalies").header("X-Tenant-Id", tenant)).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/v1/coordinator/anomalies?status=SOLVED").header("X-Tenant-Id", tenant).header("X-User-Id", coordinator.toString())).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/v1/coordinator/anomalies/not-an-id").header("X-Tenant-Id", tenant).header("X-User-Id", coordinator.toString())).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Two coordinators dismissing the same alert at once")
  void twoCoordinatorsDismissingTheSameAlertAtOnce() throws Exception {
    publishUnverified(UUID.randomUUID(), List.of(student));
    UUID anomaly = onlyAnomaly();

    CyclicBarrier bothReady = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Integer> statuses = new ArrayList<>();
    try {
      List<Future<Integer>> answers = new ArrayList<>();
      for (UUID who : List.of(coordinator, otherCoordinator)) {
        answers.add(
            pool.submit(
                () -> {
                  bothReady.await();
                  try {
                    return dismiss(tenant, who, anomaly, "{\"reason\": \"Looked at by " + who + "\"}").andReturn().getResponse().getStatus();
                  } catch (Exception failure) {
                    // What the server would have answered: MockMvc rethrows it.
                    return 500;
                  }
                }));
      }
      for (Future<Integer> answer : answers) {
        statuses.add(answer.get());
      }
    } finally {
      pool.shutdownNow();
    }

    Collections.sort(statuses);
    assertThat(statuses).containsExactly(200, 409);
    assertThat(statusOf(anomaly)).isEqualTo("DISMISSED");
    assertThat(jdbc.queryForObject("select count(*) from audit.activity_log where tenant_id = ? and action = 'ANOMALY_DISMISSED'", Long.class, tenant)).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private SessionCompleted completedEvent(String forTenant, UUID session, UUID forTutor, UUID forStudent, Instant at) {
    return new SessionCompleted(forTenant, session, UUID.randomUUID(), forTutor, forStudent, item, Credits.of(1), at);
  }

  private SessionUnverified unverifiedEvent(UUID session, List<UUID> unconfirmed) {
    return new SessionUnverified(tenant, session, UUID.randomUUID(), tutor, student, unconfirmed, Instant.now());
  }

  private void publish(Object event) {
    transactions.executeWithoutResult(status -> events.publishEvent(event));
  }

  private void publishCompleted(String forTenant, UUID session, UUID forTutor, UUID forStudent, Instant at) {
    publish(completedEvent(forTenant, session, forTutor, forStudent, at));
  }

  private void publishUnverified(UUID session, List<UUID> unconfirmed) {
    publish(unverifiedEvent(session, unconfirmed));
  }

  /** {@code count} sessions of the pair, one per day, the first one {@code daysAgo} days ago. */
  private List<UUID> completedSessions(UUID forTutor, UUID forStudent, int count, int daysAgo) {
    List<UUID> created = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      UUID session = UUID.randomUUID();
      publishCompleted(tenant, session, forTutor, forStudent, Instant.now().minus(Duration.ofDays(daysAgo + i)));
      created.add(session);
    }
    return created;
  }

  /** A completed one hour session that sessions says lasted {@code lasted}. */
  private UUID completedWithDuration(Instant start, Duration lasted) {
    UUID session = UUID.randomUUID();
    List<SessionSummary> known = new ArrayList<>(sessions.completedSessionsOf(tutor));
    known.add(new SessionSummary(session, UUID.randomUUID(), student, start, start.plus(lasted), 1));
    when(sessions.completedSessionsOf(tutor)).thenReturn(known);
    publishCompleted(tenant, session, tutor, student, Instant.now());
    return session;
  }

  /** What the job does for the current university, through the use case the job calls. */
  private int analyse() {
    AtomicInteger raised = new AtomicInteger();
    TenantContext.runAs(
        tenant,
        () -> {
          for (DetectRepeatedPairsUseCase.Pair pair : repeatedPairs.candidates()) {
            if (repeatedPairs.detect(pair)) {
              raised.incrementAndGet();
            }
          }
        });
    return raised.get();
  }

  private List<?> openAlertsOf(UUID session) {
    List<Object> found = new ArrayList<>();
    TenantContext.runAs(tenant, () -> found.addAll(auditApi.openAlertsOf(List.of(session)).getOrDefault(session, List.of())));
    return found;
  }

  private String alerts(String query) throws Exception {
    return mockMvc
        .perform(get("/api/v1/coordinator/anomalies" + query).header("X-Tenant-Id", tenant).header("X-User-Id", coordinator.toString()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private ResultActions open(String forTenant, UUID person, UUID anomaly) throws Exception {
    return mockMvc.perform(get("/api/v1/coordinator/anomalies/" + anomaly).header("X-Tenant-Id", forTenant).header("X-User-Id", person.toString()));
  }

  private ResultActions dismiss(String forTenant, UUID person, UUID anomaly, String body) throws Exception {
    return mockMvc.perform(
        post("/api/v1/coordinator/anomalies/" + anomaly + "/dismiss")
            .header("X-Tenant-Id", forTenant)
            .header("X-User-Id", person.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private long anomalies(String where) {
    return jdbc.queryForObject("select count(*) from audit.anomalies where tenant_id = ? " + where, Long.class, tenant);
  }

  private long rows(String where, Object... args) {
    Object[] all = new Object[args.length + 1];
    all[0] = tenant;
    System.arraycopy(args, 0, all, 1, args.length);
    return jdbc.queryForObject("select count(*) from audit.activity_log where tenant_id = ? and " + where, Long.class, all);
  }

  private UUID onlyAnomaly() {
    return jdbc.queryForObject("select id from audit.anomalies where tenant_id = ?", UUID.class, tenant);
  }

  private UUID anomalyOf(UUID session) {
    return jdbc.queryForObject("select anomaly_id from audit.anomaly_sessions where tenant_id = ? and session_id = ?", UUID.class, tenant, session);
  }

  private String statusOf(UUID anomaly) {
    return jdbc.queryForObject("select status from audit.anomalies where id = ?", String.class, anomaly);
  }

  private List<UUID> supportingSessionsOf(UUID anomaly) {
    return jdbc.queryForList("select session_id from audit.anomaly_sessions where anomaly_id = ? and tenant_id = ?", UUID.class, anomaly, tenant);
  }
}
