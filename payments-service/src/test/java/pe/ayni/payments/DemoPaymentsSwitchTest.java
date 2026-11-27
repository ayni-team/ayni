package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.payments.InternalRequests.asMonolith;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.payments.application.PaymentCircuit;
import pe.ayni.payments.application.PendingPurchaseReconciler;
import pe.ayni.payments.application.SimulatedPaymentSettings;
import pe.ayni.payments.application.SimulatedPaymentSettings.Outcome;

/**
 * The switch of the simulated payment provider under the {@code demo} profile, over HTTP as the
 * monolith forwards it, against the real provider, circuit and purchases. One test per scenario of
 * {@code features/demo-payments-switch.feature}, with the same display name. That only a platform
 * administrator reaches it is decided by the monolith, and tested there.
 */
@SpringBootTest
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoPaymentsSwitchTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  private static final String PAYMENTS = "/api/v1/admin/demo/payments";
  private static final String UPC = "UPC";

  @Autowired private MockMvc mockMvc;
  @Autowired private Clock clock;
  @Autowired private SimulatedPaymentSettings settings;
  @Autowired private PaymentCircuit circuit;
  @Autowired private PendingPurchaseReconciler reconciler;

  /** A platform administrator has no university: the monolith forwards only who they are. */
  private final UUID administrator = UUID.randomUUID();
  private final UUID student = UUID.randomUUID();
  private final JdbcTemplate owner = PaymentsTestDatabase.owner();

  @BeforeEach
  void theProviderAsConfigured() {
    putTheProviderBackAsConfigured();
  }

  /** The settings and the circuit belong to the context every test of the class shares. */
  @AfterEach
  void putTheProviderBackAsConfigured() {
    settings.switchTo(Outcome.CONFIRMED, Outcome.CONFIRMED);
    circuit.reset();
  }

  private ResultActions switchTo(UUID who, String body) throws Exception {
    return mockMvc.perform(
        asMonolith(put(PAYMENTS), null, who).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions purchase(UUID who) throws Exception {
    return mockMvc.perform(
        asMonolith(post("/api/v1/payments/purchases"), UPC, who)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"credits\":1}"));
  }

  private ResultActions state() throws Exception {
    return mockMvc
        .perform(asMonolith(get(PAYMENTS), null, administrator))
        .andExpect(status().isOk());
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
        .perform(asMonolith(get("/api/v1/payments/purchases/" + purchaseId), UPC, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));

    switchTo(administrator, "{\"resolutionOutcome\": \"CONFIRMED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chargeOutcome").value("PENDING"))
        .andExpect(jsonPath("$.resolutionOutcome").value("CONFIRMED"));
    reconciler.reconcile();

    mockMvc
        .perform(asMonolith(get("/api/v1/payments/purchases/" + purchaseId), UPC, student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    assertThat(
            owner.queryForObject(
                "select count(*) from payments.outbox where aggregate_id = ?::uuid",
                Integer.class,
                purchaseId))
        .isEqualTo(1);
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
        .perform(asMonolith(post(PAYMENTS + "/circuit/reset"), null, administrator))
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
}
