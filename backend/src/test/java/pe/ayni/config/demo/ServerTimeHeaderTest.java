package pe.ayni.config.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The header over HTTP, against a real PostgreSQL, under the {@code demo} profile because one
 * scenario needs its clock. One test per scenario of {@code features/W25-server-time.feature}, with
 * the same display name.
 */
@SpringBootTest(
    properties = {
      "ayni.test.identity-headers=false",
      "ayni.wallet.expiry-cron=-",
      "ayni.booking.generation-cron=-",
      "ayni.booking.hold-release-delay=PT24H",
      "ayni.sessions.presence-check-delay=PT24H",
      "ayni.sessions.closing-delay=PT24H",
      "ayni.sessions.no-show-delay=PT24H",
      "ayni.audit.analysis-delay=PT24H",
      "ayni.payments.reconciliation-delay=PT24H"
    })
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class ServerTimeHeaderTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = DemoTestDatabase.INSTANCE;

  private static final String HEADER = "X-Server-Time";
  private static final String PUBLIC_ROUTE = "/api/v1/access/university?domain=nowhere.example";
  private static final String PRIVATE_ROUTE = "/api/v1/me";

  @Autowired private MockMvc mockMvc;
  @Autowired private AdjustableClock adjustableClock;

  /** The clock is shared by every test of the context: each one leaves it at the real time. */
  @AfterEach
  void backToTheRealTime() {
    adjustableClock.reset();
  }

  private Instant serverTimeOf(String route) throws Exception {
    return Instant.parse(
        mockMvc.perform(get(route)).andReturn().getResponse().getHeader(HEADER));
  }

  @Test
  @DisplayName("An answer of the API says the time of the server")
  void anAnswerOfTheApiSaysTheTimeOfTheServer() throws Exception {
    // Whatever the answer: a 404 of a controller and the 401 of the access filter.
    assertThat(serverTimeOf(PUBLIC_ROUTE)).isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
    assertThat(serverTimeOf(PRIVATE_ROUTE)).isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
  }

  @Test
  @DisplayName("The header follows the demo clock when it is moved")
  void theHeaderFollowsTheDemoClockWhenItIsMoved() throws Exception {
    adjustableClock.advance(Duration.ofHours(2));

    assertThat(serverTimeOf(PUBLIC_ROUTE))
        .isCloseTo(Instant.now().plus(Duration.ofHours(2)), within(10, ChronoUnit.SECONDS));
  }

  @Test
  @DisplayName("An answer outside the API does not carry it")
  void anAnswerOutsideTheApiDoesNotCarryIt() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(header().doesNotExist(HEADER));
  }

  @Test
  @DisplayName("A web application on another origin can read it")
  void aWebApplicationOnAnotherOriginCanReadIt() throws Exception {
    String exposed =
        mockMvc
            .perform(get(PUBLIC_ROUTE).header(HttpHeaders.ORIGIN, "http://localhost:5173"))
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);

    assertThat(exposed).contains(HEADER);
  }
}
