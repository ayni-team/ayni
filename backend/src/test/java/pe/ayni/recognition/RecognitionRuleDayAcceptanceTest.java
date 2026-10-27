package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.recognition.domain.model.RecognitionRule;
import pe.ayni.recognition.infrastructure.RecognitionRuleRepository;
import pe.ayni.sessions.SessionsApi;

/**
 * The scenario of {@code features/US27-recognition-progress.feature} about the day a rule starts,
 * over HTTP with a real session token (TS03), with the same name.
 *
 * <p>The application's clock is fixed at 02:00 UTC of the 16th, which is 21:00 of the 15th in Lima,
 * the zone of the university: the day in UTC and the day of the university disagree.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@Import(RecognitionRuleDayAcceptanceTest.NineInTheEveningInLima.class)
class RecognitionRuleDayAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  static final Instant NOW = Instant.parse("2026-10-16T02:00:00Z");

  @TestConfiguration(proxyBeanMethods = false)
  static class NineInTheEveningInLima {
    @Bean
    @Primary
    Clock nineInTheEveningInLima() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private RecognitionRuleRepository rules;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;
  @MockitoBean private SessionsApi sessions;

  @Test
  @DisplayName("The rule in force is the one of the university's day")
  void theRuleInForceIsTheOneOfTheUniversitysDay() throws Exception {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    String tenant = people.aUniversity("D");
    Person student = people.aStudent(tenant);
    when(sessions.completedSessionsOf(student.id())).thenReturn(List.of());
    // Given a rule of the university that starts tomorrow in Lima, and the one it replaces
    rules.save(new RecognitionRule(UUID.randomUUID(), tenant, 20, null, LocalDate.of(2026, 1, 1), NOW));
    rules.save(new RecognitionRule(UUID.randomUUID(), tenant, 10, null, LocalDate.of(2026, 10, 16), NOW));

    // When the student reads their progress at nine in the evening in Lima
    String progress =
        mockMvc
            .perform(
                get("/api/v1/recognition/progress")
                    .header("Authorization", "Bearer " + student.token()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then the requirement is the one of the rule in force that day in Lima: the 16th has not come.
    assertThat(JsonPath.<Integer>read(progress, "$.requiredHours")).isEqualTo(20);
  }
}
