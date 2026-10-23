package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.payments.application.PaymentCircuit;
import pe.ayni.payments.application.PendingPurchaseReconciler;
import pe.ayni.payments.application.SimulatedPaymentSettings;
import pe.ayni.payments.application.SimulatedPaymentSettings.Outcome;

/**
 * The switch of the simulated payment provider under the {@code demo} profile, over HTTP and with
 * real session tokens, against the real provider, circuit and purchases. One test per scenario of
 * {@code features/demo-payments-switch.feature}, with the same display name.
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
      "ayni.audit.analysis-delay=PT24H",
      "ayni.payments.reconciliation-delay=PT24H"
    })
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoPaymentsSwitchTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  @RegisterExtension
  static final GreenMailExtension INBOX = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String PAYMENTS = "/api/v1/admin/demo/payments";
  private static final String UPC = "UPC";

  @Autowired private MockMvc mockMvc;
  @Autowired private Clock clock;
  @Autowired private SimulatedPaymentSettings settings;
  @Autowired private PaymentCircuit circuit;
  @Autowired private PendingPurchaseReconciler reconciler;
  @Autowired private TenantRepository tenants;
  @Autowired private UserRepository users;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private PlatformAdminRepository admins;
  @Autowired private AccessTokenGenerator tokens;

  private SignedInPeople people;
  private String administrator;
  private Person student;

  @BeforeEach
  void anAdministratorAndAStudentSignedIn() {
    putTheProviderBackAsConfigured();
    people = new SignedInPeople(tenants, users, userSessions, tokens);
    administrator = PaymentsTestAdministrators.aSignedInAdministrator(admins, userSessions, tokens);
    student = people.aStudent(UPC);
  }

  /** The settings and the circuit belong to the context every test of the class shares. */
  @AfterEach
  void putTheProviderBackAsConfigured() {
    settings.switchTo(Outcome.CONFIRMED, Outcome.CONFIRMED);
    circuit.reset();
  }

  private static MockHttpServletRequestBuilder bearer(
      MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", "Bearer " + token);
  }

  private ResultActions switchTo(String token, String body) throws Exception {
    return mockMvc.perform(
        bearer(put(PAYMENTS), token).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions purchase(Person who) throws Exception {
    return mockMvc.perform(
        bearer(post("/api/v1/payments/purchases"), who.token())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"credits\":1}"));
  }

  private ResultActions state() throws Exception {
    return mockMvc.perform(bearer(get(PAYMENTS), administrator)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Switching the charge outcome changes the next purchase without a restart")
  void switchingTheChargeOutcomeChangesTheNextPurchaseWithoutARestart() throws Exception {
    purchase(student)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    switchTo(administrator, "{\"chargeOutcome\": \"REJECTED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chargeOutcome").value("REJECTED"))
        .andExpect(jsonPath("$.resolutionOutcome").value("CONFIRMED"));
    purchase(student)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("FAILED"));

    switchTo(administrator, "{\"chargeOutcome\": \"PENDING\"}").andExpect(status().isOk());
    purchase(student)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.providerUnavailable").value(false));

    switchTo(administrator, "{\"chargeOutcome\": \"CONFIRMED\"}").andExpect(status().isOk());
    purchase(student)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    state()
        .andExpect(jsonPath("$.chargeOutcome").value("CONFIRMED"))
        .andExpect(jsonPath("$.circuit.state").value("CLOSED"));
  }

  @Test
  @DisplayName("A pending purchase is confirmed after the resolution outcome is switched")
  void aPendingPurchaseIsConfirmedAfterTheResolutionOutcomeIsSwitched() throws Exception {
    switchTo(administrator, "{\"chargeOutcome\": \"PENDING\", \"resolutionOutcome\": \"PENDING\"}")
        .andExpect(status().isOk());
    String purchaseId =
        JsonPath.read(
            purchase(student)
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    reconciler.reconcile();
    mockMvc
        .perform(bearer(get("/api/v1/payments/purchases/" + purchaseId), student.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    switchTo(administrator, "{\"resolutionOutcome\": \"CONFIRMED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chargeOutcome").value("PENDING"))
        .andExpect(jsonPath("$.resolutionOutcome").value("CONFIRMED"));
    reconciler.reconcile();

    mockMvc
        .perform(bearer(get("/api/v1/payments/purchases/" + purchaseId), student.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    mockMvc
        .perform(bearer(get("/api/v1/wallet"), student.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(1));
  }

  @Test
  @DisplayName("Three unavailable answers open the circuit and the administrator reads it")
  void threeUnavailableAnswersOpenTheCircuitAndTheAdministratorReadsIt() throws Exception {
    state().andExpect(jsonPath("$.circuit.state").value("CLOSED"));
    switchTo(administrator, "{\"chargeOutcome\": \"UNAVAILABLE\"}").andExpect(status().isOk());

    for (int attempt = 1; attempt <= 3; attempt++) {
      purchase(student)
          .andExpect(status().isAccepted())
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.providerUnavailable").value(true));
    }

    ResultActions open =
        state()
            .andExpect(jsonPath("$.chargeOutcome").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.circuit.state").value("OPEN"))
            .andExpect(jsonPath("$.circuit.consecutiveFailures").value(3));
    Instant openUntil =
        Instant.parse(
            JsonPath.read(open.andReturn().getResponse().getContentAsString(), "$.circuit.openUntil"));
    assertThat(openUntil).isAfter(clock.instant());
    assertThat(Duration.between(clock.instant(), openUntil)).isLessThanOrEqualTo(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("Resetting the circuit lets the next purchase ask the provider again")
  void resettingTheCircuitLetsTheNextPurchaseAskTheProviderAgain() throws Exception {
    switchTo(administrator, "{\"chargeOutcome\": \"UNAVAILABLE\"}").andExpect(status().isOk());
    for (int attempt = 1; attempt <= 3; attempt++) {
      purchase(student).andExpect(status().isAccepted());
    }
    state().andExpect(jsonPath("$.circuit.state").value("OPEN"));
    switchTo(administrator, "{\"chargeOutcome\": \"CONFIRMED\"}").andExpect(status().isOk());
    // The circuit is open, so the provider is not asked although it would confirm.
    purchase(student)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.providerUnavailable").value(true));

    mockMvc
        .perform(bearer(post(PAYMENTS + "/circuit/reset"), administrator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.circuit.state").value("CLOSED"))
        .andExpect(jsonPath("$.circuit.consecutiveFailures").value(0))
        .andExpect(jsonPath("$.circuit.openUntil").doesNotExist());

    purchase(student)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  @DisplayName("An outcome that does not exist is refused and nothing changes")
  void anOutcomeThatDoesNotExistIsRefusedAndNothingChanges() throws Exception {
    switchTo(administrator, "{\"chargeOutcome\": \"REJECTED\", \"resolutionOutcome\": \"MAYBE\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "resolutionOutcome must be one of CONFIRMED, REJECTED, PENDING, UNAVAILABLE"
                        + " but was 'MAYBE'"))
        .andExpect(jsonPath("$.path").value(PAYMENTS));
    switchTo(administrator, "{}").andExpect(status().isBadRequest());
    switchTo(administrator, "not json").andExpect(status().isBadRequest());

    state()
        .andExpect(jsonPath("$.chargeOutcome").value("CONFIRMED"))
        .andExpect(jsonPath("$.resolutionOutcome").value("CONFIRMED"));
  }

  @Test
  @DisplayName("A student cannot switch the payment provider")
  void aStudentCannotSwitchThePaymentProvider() throws Exception {
    mockMvc.perform(bearer(get(PAYMENTS), student.token())).andExpect(status().isForbidden());
    switchTo(student.token(), "{\"chargeOutcome\": \"REJECTED\"}")
        .andExpect(status().isForbidden());
    mockMvc
        .perform(bearer(post(PAYMENTS + "/circuit/reset"), student.token()))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(PAYMENTS)).andExpect(status().isUnauthorized());

    assertThat(settings.chargeOutcome()).isEqualTo(Outcome.CONFIRMED);
  }
}
