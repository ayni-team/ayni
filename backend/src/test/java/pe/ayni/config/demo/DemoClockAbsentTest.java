package pe.ayni.config.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * Without the {@code demo} profile, as under {@code dev} and in a deployment: the clock is the
 * real one and the routes to move it do not exist, for an administrator either.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class DemoClockAbsentTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = DemoTestDatabase.INSTANCE;

  private static final String CLOCK = "/api/v1/admin/demo/clock";

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext context;
  @Autowired private Environment environment;
  @Autowired private Clock clock;
  @Autowired private PlatformAdminRepository admins;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  @Test
  @DisplayName("Without the demo profile the clock cannot be moved")
  void withoutTheDemoProfileTheClockCannotBeMoved() throws Exception {
    String administrator = DemoAdministrators.aSignedInAdministrator(admins, userSessions, tokens);

    mockMvc
        .perform(get(CLOCK).header("Authorization", "Bearer " + administrator))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post(CLOCK + "/advance")
                .header("Authorization", "Bearer " + administrator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"by\": \"PT2H\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put(CLOCK)
                .header("Authorization", "Bearer " + administrator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"at\": \"" + Instant.now().plus(Duration.ofDays(1)) + "\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete(CLOCK).header("Authorization", "Bearer " + administrator))
        .andExpect(status().isNotFound());

    assertThat(clock).isNotInstanceOf(AdjustableClock.class);
    assertThat(context.getBeanNamesForType(AdjustableClock.class)).isEmpty();
    assertThat(context.getBeanNamesForType(DemoClockController.class)).isEmpty();
    assertThat(Duration.between(Instant.now(), clock.instant()).abs())
        .isLessThan(Duration.ofSeconds(1));
    // What the demo profile changes stays as it was everywhere else.
    assertThat(environment.getProperty("ayni.identity.session-ttl")).isEqualTo("P7D");
    assertThat(environment.getProperty("ayni.wallet.expiry-cron")).isEqualTo("0 0 3 * * *");
    assertThat(environment.getProperty("ayni.sessions.video.sign-with-real-time")).isNull();
  }
}
