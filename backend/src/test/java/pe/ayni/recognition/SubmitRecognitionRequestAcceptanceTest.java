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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import pe.ayni.recognition.application.SessionRatings;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.events.RecognitionRequested;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * US28, over HTTP, against a real database and the real wallet.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US28-submit-recognition-request.feature},
 * with the same names. The identity of each person, the sessions they taught and their bookings are
 * stubbed, since identity, sessions and booking are other modules; everything else is the real
 * application.
 *
 * <p>Each test works in a university of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class SubmitRecognitionRequestAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID student = UUID.randomUUID();
  private final UUID skill = UUID.randomUUID();
  private final List<SessionSummary> taught = new ArrayList<>();

  @Autowired private MockMvc mockMvc;
  @Autowired private RecognitionRuleRepository rules;
  @Autowired private WalletApi wallet;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents applicationEvents;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private SessionsApi sessions;
  @MockitoBean private BookingApi booking;
  @MockitoBean private SessionRatings ratings;

  private String tenant;

  /** Background: a student of a university of its own that asks for 20 hours. */
  @BeforeEach
  void aStudentOfAUniversityThatAsksForTwentyHours() {
    tenant = "T" + UUID.randomUUID().toString().substring(0, 8);
    taught.clear();
    when(identity.requireUser(any(UUID.class)))
        .thenAnswer(
            call -> {
              UUID id = call.getArgument(0);
              if (!student.equals(id)) {
                throw new NoSuchElementException("user %s not found".formatted(id));
              }
              return new UserView(id, tenant, UserRole.STUDENT, "ana@upc.edu.pe", "U202310949", "Ana Torres", null, null, null);
            });
    when(sessions.completedSessionsOf(student)).thenAnswer(call -> List.copyOf(taught));
    when(booking.requireBooking(any(UUID.class)))
        .thenAnswer(
            call ->
                new BookingView(call.getArgument(0), UUID.randomUUID(), student, skill, NOW, NOW, 1, "need", BookingStatus.COMPLETED));
    when(ratings.starsOf(any())).thenReturn(Map.of());
    rules.save(new RecognitionRule(UUID.randomUUID(), tenant, 20, null, LocalDate.of(2026, 1, 1), NOW));
  }

  /** Sessions in the order given, the first one the oldest. */
  private List<SessionSummary> taughtHours(int... hoursPerSession) {
    List<SessionSummary> added = new ArrayList<>();
    for (int hours : hoursPerSession) {
      Instant start = NOW.minusSeconds(86400L * (30 - taught.size()));
      SessionSummary session =
          new SessionSummary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(3600L * hours), hours);
      taught.add(session);
      added.add(session);
    }
    return added;
  }

  private ResultActions submit(UUID user) throws Exception {
    return mockMvc.perform(post("/api/v1/recognition/requests").header("X-Tenant-Id", tenant).header("X-User-Id", user.toString()));
  }

  private ResultActions mine(UUID user) throws Exception {
    return mockMvc.perform(get("/api/v1/recognition/requests/mine").header("X-Tenant-Id", tenant).header("X-User-Id", user.toString()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private int requestsOfTheUniversity() {
    return jdbc.queryForObject("select count(*) from recognition.requests where tenant_id = ?", Integer.class, tenant);
  }

  @Test
  @DisplayName("Sending the request")
  void sendingTheRequest() throws Exception {

    List<SessionSummary> three = taughtHours(10, 10, 5);
    when(ratings.starsOf(List.of(three.get(0).sessionId(), three.get(1).sessionId())))
        .thenReturn(Map.of(three.get(0).sessionId(), 5, three.get(1).sessionId(), 4));

    String answer = body(submit(student).andExpect(status().isCreated()));

    assertThat(JsonPath.<String>read(answer, "$.status")).isEqualTo("SUBMITTED");
    assertThat(JsonPath.<Integer>read(answer, "$.totalHours")).isEqualTo(20);
    assertThat(JsonPath.<Integer>read(answer, "$.sessionsCount")).isEqualTo(2);
    assertThat(JsonPath.<Double>read(answer, "$.averageRating")).isEqualTo(4.5);
    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[*].sessionId"))
        .containsExactly(three.get(0).sessionId().toString(), three.get(1).sessionId().toString());
    assertThat(JsonPath.<List<Integer>>read(answer, "$.sessions[*].stars")).containsExactly(5, 4);
    assertThat(JsonPath.<List<String>>read(answer, "$.sessions[*].catalogItemId")).containsOnly(skill.toString());
    UUID requestId = UUID.fromString(JsonPath.read(answer, "$.id"));
    assertThat(applicationEvents.stream(RecognitionRequested.class).filter(event -> event.requestId().equals(requestId)))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.tenantId()).isEqualTo(tenant);
              assertThat(event.studentId()).isEqualTo(student);
              assertThat(event.totalHours()).isEqualTo(20);
            });
  }

  @Test
  @DisplayName("Credits are not consumed")
  void creditsAreNotConsumed() throws Exception {

    taughtHours(10, 10, 5);
    TenantContext.runAs(tenant, () -> wallet.grant(student, Credits.of(25), CreditType.EARNED, null, UUID.randomUUID()));

    submit(student).andExpect(status().isCreated());

    AtomicReference<Credits> available = new AtomicReference<>();
    TenantContext.runAs(tenant, () -> available.set(wallet.balanceOf(student).available()));
    assertThat(available.get()).isEqualTo(Credits.of(25));
  }

  @Test
  @DisplayName("The same hours are not presented twice")
  void theSameHoursAreNotPresentedTwice() throws Exception {

    List<SessionSummary> three = taughtHours(12, 12, 12);
    String first = body(submit(student).andExpect(status().isCreated()));
    assertThat(JsonPath.<List<String>>read(first, "$.sessions[*].sessionId"))
        .containsExactly(three.get(0).sessionId().toString(), three.get(1).sessionId().toString());

    String refusal = body(submit(student).andExpect(status().isConflict()));

    assertThat(JsonPath.<Integer>read(refusal, "$.missingHours")).isEqualTo(8);
    assertThat(requestsOfTheUniversity()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from recognition.request_sessions where tenant_id = ? and session_id = ?",
                Integer.class,
                tenant,
                three.get(0).sessionId()))
        .isEqualTo(1);
    String progress =
        body(
            mockMvc
                .perform(get("/api/v1/recognition/progress").header("X-Tenant-Id", tenant).header("X-User-Id", student.toString()))
                .andExpect(status().isOk()));
    assertThat(JsonPath.<Integer>read(progress, "$.earnedHours")).isEqualTo(12);
  }

  @Test
  @DisplayName("Insufficient hours")
  void insufficientHours() throws Exception {

    taughtHours(10, 6);

    String refusal = body(submit(student).andExpect(status().isConflict()));

    assertThat(JsonPath.<Integer>read(refusal, "$.missingHours")).isEqualTo(4);
    assertThat(JsonPath.<String>read(refusal, "$.message")).contains("4 more hours are needed");
    assertThat(requestsOfTheUniversity()).isZero();
    assertThat(mine(student).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo("[]");
  }

  @Test
  @DisplayName("Following the request")
  void followingTheRequest() throws Exception {

    taughtHours(10, 10);
    String submitted = body(submit(student).andExpect(status().isCreated()));
    UUID requestId = UUID.fromString(JsonPath.read(submitted, "$.id"));

    String before = body(mine(student).andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(before, "$[*].status")).containsExactly("SUBMITTED");
    assertThat(JsonPath.<List<Integer>>read(before, "$[*].totalHours")).containsExactly(20);
    assertThat(JsonPath.<List<Integer>>read(before, "$[0].sessions[*].hours")).containsExactly(10, 10);
    assertThat(JsonPath.<Object>read(before, "$[0].decisionReason")).isNull();

    jdbc.update(
        "update recognition.requests set status = 'APPROVED', reviewed_by = ?, reviewed_at = now(), decision_reason = ? where id = ?",
        UUID.randomUUID(),
        "The hours match the extracurricular credit of the programme",
        requestId);

    String after = body(mine(student).andExpect(status().isOk()));
    assertThat(JsonPath.<List<String>>read(after, "$[*].status")).containsExactly("APPROVED");
    assertThat(JsonPath.<List<String>>read(after, "$[*].decisionReason"))
        .containsExactly("The hours match the extracurricular credit of the programme");
    assertThat(JsonPath.<List<String>>read(after, "$[*].reviewedAt")).hasSize(1);
  }

  @Test
  @DisplayName("A university that has not opened recognition")
  void aUniversityThatHasNotOpenedRecognition() throws Exception {

    String closed = "C" + tenant;
    when(identity.requireUser(student))
        .thenReturn(new UserView(student, closed, UserRole.STUDENT, "ana@u.pe", "U1", "Ana Torres", null, null, null));
    taughtHours(30);

    mockMvc
        .perform(post("/api/v1/recognition/requests").header("X-Tenant-Id", closed).header("X-User-Id", student.toString()))
        .andExpect(status().isConflict());

    assertThat(jdbc.queryForObject("select count(*) from recognition.requests where tenant_id = ?", Integer.class, closed))
        .isZero();
  }

  @Test
  @DisplayName("A person who is not of the university")
  void aPersonWhoIsNotOfTheUniversity() throws Exception {

    UUID stranger = UUID.randomUUID();

    submit(stranger).andExpect(status().isNotFound());
    mine(stranger).andExpect(status().isNotFound());
  }
}
