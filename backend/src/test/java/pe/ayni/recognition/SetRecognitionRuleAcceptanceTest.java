package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.booking.BookingApi;
import pe.ayni.booking.BookingStatus;
import pe.ayni.booking.BookingView;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.recognition.application.SessionRatings;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;

/**
 * US27 and US28, the rule of the university, over HTTP with real session tokens against a real
 * database.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US27-recognition-rule.feature},
 * with the same names, except the one about two coordinators at once, which is in {@code
 * SetRecognitionRuleConcurrencyTest}. Identity is the real one; the sessions a student taught, their
 * booking and their ratings are stubbed, since they belong to other modules.
 *
 * <p>The clock stands at 15:00 UTC of the 20th, which is 10:00 of the same day in Lima.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@Import(SetRecognitionRuleAcceptanceTest.TenInTheMorningInLima.class)
class SetRecognitionRuleAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
  static final String TODAY = "2026-10-20";
  static final String RULE = "/api/v1/coordinator/recognition/rule";

  @TestConfiguration(proxyBeanMethods = false)
  static class TenInTheMorningInLima {
    @Bean
    @Primary
    Clock tenInTheMorningInLima() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @MockitoBean private SessionsApi sessions;
  @MockitoBean private BookingApi booking;
  @MockitoBean private SessionRatings ratings;

  private SignedInPeople people;
  private String tenant;
  private Person coordinator;
  private Person student;

  /** Background: an active coordinator and a student of a university in Lima, of its own for each test. */
  @BeforeEach
  void aCoordinatorAndAStudentOfAUniversityInLima() {
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    tenant = people.aUniversity("R");
    coordinator = people.aCoordinator(tenant);
    student = people.aStudent(tenant);
    when(sessions.completedSessionsOf(any())).thenReturn(List.of());
  }

  private ResultActions set(Person who, String json) throws Exception {
    return mockMvc.perform(
        put(RULE).header("Authorization", "Bearer " + who.token()).contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions read(Person who) throws Exception {
    return mockMvc.perform(get(RULE).header("Authorization", "Bearer " + who.token()));
  }

  private ResultActions progressOf(Person who) throws Exception {
    return mockMvc.perform(get("/api/v1/recognition/progress").header("Authorization", "Bearer " + who.token()));
  }

  private static String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private void theUniversityAsksFor(int hours) throws Exception {
    set(coordinator, "{\"minimumHours\": %d}".formatted(hours)).andExpect(status().isOk());
  }

  private List<Map<String, Object>> rulesOf(String code) {
    return jdbc.queryForList(
        "select minimum_hours, valid_from, superseded_at from recognition.rules where tenant_id = ? order by minimum_hours",
        code);
  }

  @Test
  @DisplayName("Setting the rule opens recognition")
  void settingTheRuleOpensRecognition() throws Exception {
    // Given the university has no recognition rule
    read(coordinator).andExpect(status().isNotFound());
    assertThat(JsonPath.<Object>read(body(progressOf(student).andExpect(status().isOk())), "$.requiredHours")).isNull();

    // When the coordinator sets the rule to 20 hours
    String answer = body(set(coordinator, "{\"minimumHours\": 20}").andExpect(status().isOk()));

    // Then the answer is the rule: 20 hours, from today
    assertThat(JsonPath.<Integer>read(answer, "$.minimumHours")).isEqualTo(20);
    assertThat(JsonPath.<String>read(answer, "$.validFrom")).isEqualTo(TODAY);
    // And the coordinator reads the same rule
    String reread = body(read(coordinator).andExpect(status().isOk()));
    assertThat(JsonPath.<Integer>read(reread, "$.minimumHours")).isEqualTo(20);
    assertThat(JsonPath.<String>read(reread, "$.validFrom")).isEqualTo(TODAY);
    // And the student sees that 20 hours are required
    String progress = body(progressOf(student).andExpect(status().isOk()));
    assertThat(JsonPath.<Integer>read(progress, "$.requiredHours")).isEqualTo(20);
  }

  @Test
  @DisplayName("Replacing the rule keeps the previous one")
  void replacingTheRuleKeepsThePreviousOne() throws Exception {
    // Given the university asks for 20 hours
    theUniversityAsksFor(20);

    // When the coordinator sets the rule to 30 hours
    set(coordinator, "{\"minimumHours\": 30}").andExpect(status().isOk());

    // Then the rule in force asks for 30 hours
    assertThat(JsonPath.<Integer>read(body(read(coordinator).andExpect(status().isOk())), "$.minimumHours")).isEqualTo(30);
    assertThat(JsonPath.<Integer>read(body(progressOf(student).andExpect(status().isOk())), "$.requiredHours")).isEqualTo(30);
    // And the rule of 20 hours is kept, replaced, and is no longer in force
    List<Map<String, Object>> rules = rulesOf(tenant);
    assertThat(rules).extracting(rule -> rule.get("minimum_hours")).containsExactly(20, 30);
    assertThat(rules.get(0).get("superseded_at")).isNotNull();
    assertThat(rules.get(1).get("superseded_at")).isNull();
  }

  @Test
  @DisplayName("A rule that starts later leaves the current one running")
  void aRuleThatStartsLaterLeavesTheCurrentOneRunning() throws Exception {
    // Given the university asks for 20 hours
    theUniversityAsksFor(20);

    // When the coordinator sets the rule to 30 hours from next week
    String answer = body(set(coordinator, "{\"minimumHours\": 30, \"validFrom\": \"2026-10-27\"}").andExpect(status().isOk()));
    assertThat(JsonPath.<String>read(answer, "$.validFrom")).isEqualTo("2026-10-27");

    // Then the rule in force today still asks for 20 hours
    assertThat(JsonPath.<Integer>read(body(read(coordinator).andExpect(status().isOk())), "$.minimumHours")).isEqualTo(20);
    assertThat(JsonPath.<Integer>read(body(progressOf(student).andExpect(status().isOk())), "$.requiredHours")).isEqualTo(20);
    // And the new rule is kept, ready to start on that day
    List<Map<String, Object>> rules = rulesOf(tenant);
    assertThat(rules).extracting(rule -> rule.get("minimum_hours")).containsExactly(20, 30);
    assertThat(rules).extracting(rule -> rule.get("superseded_at")).containsOnlyNulls();
    assertThat(rules.get(1).get("valid_from").toString()).isEqualTo("2026-10-27");
  }

  @Test
  @DisplayName("Hours that are not at least one")
  void hoursThatAreNotAtLeastOne() throws Exception {
    // Given the university asks for 20 hours
    theUniversityAsksFor(20);

    // When the coordinator sets the rule to 0 hours, to -5 hours, to a word, or sends no hours at all
    for (String json : List.of("{\"minimumHours\": 0}", "{\"minimumHours\": -5}", "{\"minimumHours\": \"many\"}", "{}")) {
      // Then each is refused as a malformed request
      String refusal = body(set(coordinator, json).andExpect(status().isBadRequest()));
      assertThat(JsonPath.<Integer>read(refusal, "$.status")).isEqualTo(400);
      assertThat(JsonPath.<String>read(refusal, "$.path")).isEqualTo(RULE);
      assertThat(JsonPath.<String>read(refusal, "$.message")).isNotBlank();
    }

    // And the rule in force still asks for 20 hours
    assertThat(JsonPath.<Integer>read(body(read(coordinator).andExpect(status().isOk())), "$.minimumHours")).isEqualTo(20);
    assertThat(rulesOf(tenant)).hasSize(1);
  }

  @Test
  @DisplayName("A day that already passed")
  void aDayThatAlreadyPassed() throws Exception {
    // Given the university asks for 20 hours
    theUniversityAsksFor(20);

    // When the coordinator sets the rule to start yesterday
    String refusal = body(set(coordinator, "{\"minimumHours\": 30, \"validFrom\": \"2026-10-19\"}").andExpect(status().isBadRequest()));

    // Then it is refused as a malformed request
    assertThat(JsonPath.<String>read(refusal, "$.message")).contains("validFrom");
    // And the rule in force still asks for 20 hours
    assertThat(JsonPath.<Integer>read(body(read(coordinator).andExpect(status().isOk())), "$.minimumHours")).isEqualTo(20);
    assertThat(rulesOf(tenant)).hasSize(1);
  }

  @Test
  @DisplayName("A student cannot set the rule")
  void aStudentCannotSetTheRule() throws Exception {
    // When the student sets the rule to 20 hours
    set(student, "{\"minimumHours\": 20}")
        // Then it is refused as forbidden
        .andExpect(status().isForbidden());
    read(student).andExpect(status().isForbidden());

    // And the university still has no rule
    assertThat(rulesOf(tenant)).isEmpty();
  }

  @Test
  @DisplayName("A restricted coordinator cannot set the rule")
  void aRestrictedCoordinatorCannotSetTheRule() throws Exception {
    // Given a coordinator of the university who is restricted
    Person restricted = people.signedIn(tenant, UserRole.COORDINATOR, UserStatus.RESTRICTED);

    // When they set the rule to 20 hours
    String refusal = body(set(restricted, "{\"minimumHours\": 20}").andExpect(status().isForbidden()));

    // Then it is refused as forbidden
    assertThat(JsonPath.<Integer>read(refusal, "$.status")).isEqualTo(403);
    read(restricted).andExpect(status().isForbidden());
    // And the university still has no rule
    assertThat(rulesOf(tenant)).isEmpty();
  }

  @Test
  @DisplayName("The rule of another university is not seen")
  void theRuleOfAnotherUniversityIsNotSeen() throws Exception {
    // Given another university asks for 40 hours
    String other = people.aUniversity("O");
    Person otherCoordinator = people.aCoordinator(other);
    set(otherCoordinator, "{\"minimumHours\": 40}").andExpect(status().isOk());

    // When the coordinator reads the rule of their university
    read(coordinator)
        // Then it is not found
        .andExpect(status().isNotFound());

    // When the coordinator sets the rule to 20 hours
    set(coordinator, "{\"minimumHours\": 20}").andExpect(status().isOk());

    // Then the other university still asks for 40 hours
    assertThat(JsonPath.<Integer>read(body(read(otherCoordinator).andExpect(status().isOk())), "$.minimumHours")).isEqualTo(40);
    assertThat(rulesOf(other)).hasSize(1);
    assertThat(rulesOf(other).get(0).get("superseded_at")).isNull();
  }

  @Test
  @DisplayName("Requests already submitted keep their figures")
  void requestsAlreadySubmittedKeepTheirFigures() throws Exception {
    // Given the university asks for 20 hours and the student submitted a request
    theUniversityAsksFor(20);
    List<SessionSummary> taught = new ArrayList<>();
    for (int day = 3; day >= 1; day--) {
      Instant start = NOW.minusSeconds(86400L * day);
      taught.add(new SessionSummary(UUID.randomUUID(), UUID.randomUUID(), student.id(), start, start.plusSeconds(3600L * 10), 10));
    }
    when(sessions.completedSessionsOf(student.id())).thenReturn(taught);
    when(booking.requireBooking(any(UUID.class)))
        .thenAnswer(
            call ->
                new BookingView(call.getArgument(0), UUID.randomUUID(), student.id(), UUID.randomUUID(), NOW, NOW, 1, "need", BookingStatus.COMPLETED));
    when(ratings.starsOf(any())).thenReturn(Map.of());
    mockMvc
        .perform(post("/api/v1/recognition/requests").header("Authorization", "Bearer " + student.token()))
        .andExpect(status().isCreated());
    String before = body(mockMvc.perform(get("/api/v1/recognition/requests/mine").header("Authorization", "Bearer " + student.token())).andExpect(status().isOk()));
    Map<String, Object> figures =
        jdbc.queryForMap("select total_hours, sessions_count, status from recognition.requests where tenant_id = ?", tenant);

    // When the coordinator sets the rule to 30 hours
    set(coordinator, "{\"minimumHours\": 30}").andExpect(status().isOk());

    // Then the request of the student has the same hours, count and state as before
    assertThat(jdbc.queryForMap("select total_hours, sessions_count, status from recognition.requests where tenant_id = ?", tenant))
        .isEqualTo(figures);
    assertThat(figures).containsEntry("total_hours", 20).containsEntry("sessions_count", 2).containsEntry("status", "SUBMITTED");
    // And the student reads it exactly as before
    String after = body(mockMvc.perform(get("/api/v1/recognition/requests/mine").header("Authorization", "Bearer " + student.token())).andExpect(status().isOk()));
    assertThat(after).isEqualTo(before);
    assertThat(JsonPath.<Integer>read(body(progressOf(student).andExpect(status().isOk())), "$.requiredHours")).isEqualTo(30);
  }
}
