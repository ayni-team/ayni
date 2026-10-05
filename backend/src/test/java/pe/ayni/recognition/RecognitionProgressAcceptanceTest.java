package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.IdentityApi;
import pe.ayni.identity.UserRole;
import pe.ayni.identity.UserView;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionSummary;
import pe.ayni.sessions.SessionsApi;
import pe.ayni.shared.domain.CreditType;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;
import pe.ayni.wallet.WalletApi;

/**
 * US27, over HTTP, against a real database and the real wallet.
 *
 * <p>One test per scenario of {@code src/test/resources/features/US27-recognition-progress.feature},
 * with the same names. The identity of each person and the sessions they taught are stubbed, since
 * identity and sessions are other modules; everything else is the real application.
 *
 * <p>Each test works in a university of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecognitionProgressAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

  private final UUID student = UUID.randomUUID();
  private final List<SessionSummary> taught = new ArrayList<>();

  @Autowired private MockMvc mockMvc;
  @Autowired private RecognitionRuleRepository rules;
  @Autowired private WalletApi wallet;
  @MockitoBean private IdentityApi identity;
  @MockitoBean private SessionsApi sessions;

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
    rules.save(new RecognitionRule(UUID.randomUUID(), tenant, 20, null, LocalDate.of(2026, 1, 1), NOW));
  }

  private void taughtHours(int... hoursPerSession) {
    for (int hours : hoursPerSession) {
      taught.add(
          new SessionSummary(
              UUID.randomUUID(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              NOW.minusSeconds(86400L * (taught.size() + 1)),
              NOW.minusSeconds(86400L * (taught.size() + 1)).plusSeconds(3600L * hours),
              hours));
    }
  }

  private ResultActions progress(UUID user) throws Exception {
    return mockMvc.perform(
        get("/api/v1/recognition/progress").header("X-Tenant-Id", tenant).header("X-User-Id", user.toString()));
  }

  private String progressOfStudent() throws Exception {
    return progress(student).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  @Test
  @DisplayName("Visible progress")
  void visibleProgress() throws Exception {

    taughtHours(5, 4, 3, 2);

    String answer = progressOfStudent();

    assertThat(JsonPath.<Integer>read(answer, "$.earnedHours")).isEqualTo(14);
    assertThat(JsonPath.<Integer>read(answer, "$.sessionsCount")).isEqualTo(4);
    assertThat(JsonPath.<Integer>read(answer, "$.requiredHours")).isEqualTo(20);
    assertThat(JsonPath.<Integer>read(answer, "$.missingHours")).isEqualTo(6);
    assertThat(JsonPath.<Boolean>read(answer, "$.canRequest")).isFalse();
  }

  @Test
  @DisplayName("Only earned credits count")
  void onlyEarnedCreditsCount() throws Exception {

    taughtHours(7, 7);
    TenantContext.runAs(
        tenant,
        () -> {
          wallet.grant(student, Credits.of(30), CreditType.ALLOCATED, NOW.plusSeconds(86400L * 90), UUID.randomUUID());
          wallet.grant(student, Credits.of(10), CreditType.PURCHASED, null, UUID.randomUUID());
        });

    String answer = progressOfStudent();

    assertThat(JsonPath.<Integer>read(answer, "$.earnedHours")).isEqualTo(14);
    assertThat(JsonPath.<Integer>read(answer, "$.missingHours")).isEqualTo(6);
  }

  @Test
  @DisplayName("Update after each session")
  void updateAfterEachSession() throws Exception {

    taughtHours(8, 6);

    String before = progressOfStudent();
    taughtHours(2);
    String after = progressOfStudent();

    assertThat(JsonPath.<Integer>read(before, "$.earnedHours")).isEqualTo(14);
    assertThat(JsonPath.<Integer>read(after, "$.earnedHours")).isEqualTo(16);
    assertThat(JsonPath.<Integer>read(after, "$.missingHours")).isEqualTo(4);
  }

  @Test
  @DisplayName("Goal reached")
  void goalReached() throws Exception {

    taughtHours(10, 10);

    String answer = progressOfStudent();

    assertThat(JsonPath.<Integer>read(answer, "$.missingHours")).isZero();
    assertThat(JsonPath.<Boolean>read(answer, "$.requirementMet")).isTrue();
    assertThat(JsonPath.<Boolean>read(answer, "$.canRequest")).isTrue();
  }

  @Test
  @DisplayName("A university that has not opened recognition")
  void aUniversityThatHasNotOpenedRecognition() throws Exception {

    String closed = "C" + tenant;
    when(identity.requireUser(student))
        .thenReturn(new UserView(student, closed, UserRole.STUDENT, "ana@u.pe", "U1", "Ana Torres", null, null, null));
    taughtHours(30);

    String answer =
        mockMvc
            .perform(get("/api/v1/recognition/progress").header("X-Tenant-Id", closed).header("X-User-Id", student.toString()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(JsonPath.<Integer>read(answer, "$.earnedHours")).isEqualTo(30);
    assertThat(JsonPath.<Object>read(answer, "$.requiredHours")).isNull();
    assertThat(JsonPath.<Object>read(answer, "$.missingHours")).isNull();
    assertThat(JsonPath.<Boolean>read(answer, "$.canRequest")).isFalse();
  }

  @Test
  @DisplayName("A person who is not of the university")
  void aPersonWhoIsNotOfTheUniversity() throws Exception {

    progress(UUID.randomUUID()).andExpect(status().isNotFound());
  }
}
