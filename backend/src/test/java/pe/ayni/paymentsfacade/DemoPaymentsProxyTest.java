package pe.ayni.paymentsfacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.paymentsfacade.FakePaymentsService.Reply;
import pe.ayni.paymentsfacade.PaymentsTestAdministrators.SignedInAdministrator;

/**
 * The switch of the simulated provider under the demo profile, with real session tokens: the
 * monolith decides who may reach it and forwards the administrator's call to the payments service.
 * Scenarios of {@code features/payments-gateway.feature}; what the switch does is the service's
 * ({@code payments-service/src/test/resources/features/demo-payments-switch.feature}).
 */
@SpringBootTest(
    properties = {
      "ayni.test.identity-headers=false",
      "spring.mail.host=localhost",
      "spring.mail.port=3025",
      "ayni.wallet.expiry-cron=-",
      "ayni.booking.generation-cron=-",
      "ayni.booking.hold-release-delay=PT24H",
      "ayni.sessions.presence-check-delay=PT24H",
      "ayni.sessions.closing-delay=PT24H",
      "ayni.sessions.no-show-delay=PT24H",
      "ayni.audit.analysis-delay=PT24H"
    })
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoPaymentsProxyTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsFacadeTestDatabase.INSTANCE;

  private static final FakePaymentsService SERVICE = new FakePaymentsService();

  @DynamicPropertySource
  static void theFakeService(DynamicPropertyRegistry registry) {
    registry.add("ayni.payments-service.url", SERVICE::url);
  }

  @AfterAll
  static void stopTheFakeService() {
    SERVICE.close();
  }

  private static final String PAYMENTS = "/api/v1/admin/demo/payments";
  private static final String VIEW =
      """
      {"chargeOutcome":"REJECTED","resolutionOutcome":"CONFIRMED",\
      "circuit":{"state":"CLOSED","consecutiveFailures":0,"openUntil":null}}""";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private PlatformAdminRepository admins;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInAdministrator administrator;
  private Person student;

  @BeforeEach
  void anAdministratorAndAStudentSignedIn() {
    SERVICE.received().clear();
    SERVICE.answer(call -> Reply.json(200, VIEW));
    administrator = PaymentsTestAdministrators.aSignedInAdministrator(admins, userSessions, tokens);
    student = new SignedInPeople(tenants, users, userSessions, tokens).aStudent("UPC");
  }

  @Test
  @DisplayName("The administrator's switch is forwarded to the payments service")
  void theAdministratorsSwitchIsForwarded() throws Exception {
    mockMvc
        .perform(
            put(PAYMENTS)
                .header("Authorization", "Bearer " + administrator.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chargeOutcome\": \"REJECTED\"}"))
        .andExpect(status().isOk())
        .andExpect(content().json(VIEW, true));
    mockMvc
        .perform(get(PAYMENTS).header("Authorization", "Bearer " + administrator.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.circuit.state").value("CLOSED"));
    mockMvc
        .perform(
            post(PAYMENTS + "/circuit/reset")
                .header("Authorization", "Bearer " + administrator.token()))
        .andExpect(status().isOk());

    assertThat(SERVICE.received()).hasSize(3);
    FakePaymentsService.Received forwarded = SERVICE.received().get(0);
    assertThat(forwarded.method()).isEqualTo("PUT");
    assertThat(forwarded.path()).isEqualTo(PAYMENTS);
    assertThat(forwarded.header("X-Internal-Token")).isEqualTo("test-internal-token");
    assertThat(forwarded.header("X-User-Id")).isEqualTo(administrator.id().toString());
    assertThat(forwarded.header("X-Tenant-Id")).isNull();
    assertThat(forwarded.body()).contains("\"chargeOutcome\":\"REJECTED\"");
    assertThat(SERVICE.received().get(2).path()).isEqualTo(PAYMENTS + "/circuit/reset");
  }

  @Test
  @DisplayName("An outcome that does not exist is refused with the words of the payments service")
  void anOutcomeThatDoesNotExistIsRefusedWithTheWordsOfTheService() throws Exception {
    String refusal =
        """
        {"timestamp":"2026-11-25T12:00:00Z","status":400,"error":"Bad Request",\
        "message":"chargeOutcome must be one of CONFIRMED, REJECTED, PENDING, UNAVAILABLE but was 'MAYBE'",\
        "path":"/api/v1/admin/demo/payments"}""";
    SERVICE.answer(call -> Reply.json(400, refusal));

    mockMvc
        .perform(
            put(PAYMENTS)
                .header("Authorization", "Bearer " + administrator.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chargeOutcome\": \"MAYBE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().json(refusal, true));
  }

  @Test
  @DisplayName("A student cannot switch the payment provider")
  void aStudentCannotSwitchThePaymentProvider() throws Exception {
    mockMvc
        .perform(get(PAYMENTS).header("Authorization", "Bearer " + student.token()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put(PAYMENTS)
                .header("Authorization", "Bearer " + student.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chargeOutcome\": \"REJECTED\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post(PAYMENTS + "/circuit/reset").header("Authorization", "Bearer " + student.token()))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(PAYMENTS)).andExpect(status().isUnauthorized());

    assertThat(SERVICE.received()).isEmpty();
  }
}
