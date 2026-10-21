package pe.ayni.recognition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * Two coordinators setting the rule of the same university at once, over HTTP against a real
 * PostgreSQL. The scenario "Two coordinators set the rule at the same time" of {@code
 * features/US27-recognition-rule.feature}.
 *
 * <p>Both must be answered and exactly one rule must stay in force. Without the lock on the
 * university's rules both read the same rule in force, both close it and both add one, and two stay
 * open. Take {@code rules.lockTenant} out of the use case and this test fails.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class SetRecognitionRuleConcurrencyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = RecognitionTestDatabase.INSTANCE;

  private static final String RULE = "/api/v1/coordinator/recognition/rule";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  @Test
  @DisplayName("Two coordinators set the rule at the same time")
  void twoCoordinatorsSetTheRuleAtTheSameTime() throws Exception {
    SignedInPeople people = new SignedInPeople(tenants, users, userSessions, tokens);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < 8; round++) {
        // Given the university asks for 20 hours
        String tenant = people.aUniversity("C");
        Person first = people.aCoordinator(tenant);
        Person second = people.aCoordinator(tenant);
        mockMvc
            .perform(put(RULE).header("Authorization", "Bearer " + first.token()).contentType(MediaType.APPLICATION_JSON).content("{\"minimumHours\": 20}"))
            .andReturn();

        // When two coordinators set a different rule at the same moment
        CyclicBarrier together = new CyclicBarrier(2);
        List<Future<Integer>> answers = new ArrayList<>();
        int hours = 30;
        for (Person who : List.of(first, second)) {
          String json = "{\"minimumHours\": %d}".formatted(hours++);
          Callable<Integer> attempt =
              () -> {
                together.await();
                return mockMvc
                    .perform(put(RULE).header("Authorization", "Bearer " + who.token()).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              };
          answers.add(pool.submit(attempt));
        }

        // Then both are answered and the university has exactly one rule in force
        for (Future<Integer> answer : answers) {
          assertThat(answer.get()).isEqualTo(200);
        }
        List<Map<String, Object>> rules =
            jdbc.queryForList("select minimum_hours, superseded_at from recognition.rules where tenant_id = ? order by minimum_hours", tenant);
        assertThat(rules).filteredOn(rule -> rule.get("superseded_at") == null).hasSize(1);
        // And the rules they set are kept, all but the one in force replaced
        assertThat(rules).extracting(rule -> rule.get("minimum_hours")).containsExactly(20, 30, 31);
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
