package pe.ayni.paymentsfacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.paymentsfacade.FakePaymentsService.Reply;

/**
 * The Strangler Fig's routing over HTTP, against a fake payments service on a local port: what the
 * monolith forwards, and that the answer comes back as the service gave it. Scenarios of {@code
 * features/payments-gateway.feature}, with the same names.
 */
@SpringBootTest(
    properties = {
      "spring.mail.host=localhost",
      "spring.mail.port=3025",
      "ayni.payments-service.timeout=PT1S"
    })
@AutoConfigureMockMvc
class PaymentsGatewayTest {

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

  private static final String UPC = "UPC";
  private static final String LIMIT_REACHED =
      """
      {"timestamp":"2026-11-25T12:00:00Z","status":409,"error":"Conflict",\
      "message":"Monthly purchase limit is 5 credits; 5 already used; maximum available now is 0",\
      "path":"/api/v1/payments/purchases"}""";

  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void aServiceThatAnswersLikeTheRealOne() {
    SERVICE.received().clear();
    Set<String> keys = ConcurrentHashMap.newKeySet();
    SERVICE.answer(
        call -> {
          if (call.method().equals("POST")) {
            boolean repeated = !keys.add(call.header("Idempotency-Key"));
            return Reply.json(repeated ? 200 : 201, confirmed("11111111-1111-4111-8111-111111111111"));
          }
          if (call.path().equals("/api/v1/payments/purchases")) {
            return Reply.json(200, "[" + confirmed("11111111-1111-4111-8111-111111111111") + "]");
          }
          return Reply.json(
              200,
              """
              {"id":"11111111-1111-4111-8111-111111111111","credits":2,"amount":10.00,\
              "currency":"PEN","status":"CONFIRMED","purchasedAt":"2026-11-25T12:00:00Z",\
              "confirmedAt":"2026-11-25T12:00:01Z","providerReference":"simulated-1",\
              "creditMovement":{"type":"PURCHASE",\
              "referenceId":"11111111-1111-4111-8111-111111111111",\
              "historyUrl":"/api/v1/wallet/movements?reason=PURCHASE"}}""");
        });
  }

  @Test
  @DisplayName("A purchase is forwarded with the student of the session and answered as the service answers")
  void aPurchaseIsForwardedWithTheStudentOfTheSession() throws Exception {
    String key = "gateway-" + UUID.randomUUID();

    purchase(2, key)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("11111111-1111-4111-8111-111111111111"))
        .andExpect(jsonPath("$.credits").value(2))
        .andExpect(jsonPath("$.amount").value(10.00))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.providerUnavailable").value(false));
    purchase(2, key).andExpect(status().isOk()).andExpect(jsonPath("$.credits").value(2));

    assertThat(SERVICE.received()).hasSize(2);
    FakePaymentsService.Received forwarded = SERVICE.received().get(0);
    assertThat(forwarded.method()).isEqualTo("POST");
    assertThat(forwarded.path()).isEqualTo("/api/v1/payments/purchases");
    assertThat(forwarded.header("X-Internal-Token")).isEqualTo("test-internal-token");
    assertThat(forwarded.header("X-Tenant-Id")).isEqualTo(UPC);
    assertThat(forwarded.header("X-User-Id")).isEqualTo(student.toString());
    assertThat(forwarded.header("Idempotency-Key")).isEqualTo(key);
    assertThat(forwarded.body()).isEqualTo("{\"credits\":2}");

    mockMvc
        .perform(get("/api/v1/payments/purchases").header("X-Tenant-Id", UPC).header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    mockMvc
        .perform(
            get("/api/v1/payments/purchases/11111111-1111-4111-8111-111111111111")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.creditMovement.type").value("PURCHASE"));
    assertThat(SERVICE.received().get(3).path())
        .isEqualTo("/api/v1/payments/purchases/11111111-1111-4111-8111-111111111111");
    assertThat(SERVICE.received().get(3).header("X-User-Id")).isEqualTo(student.toString());
  }

  @Test
  @DisplayName("An error of the payments service reaches the student unchanged")
  void anErrorOfThePaymentsServiceReachesTheStudentUnchanged() throws Exception {
    SERVICE.answer(call -> Reply.json(409, LIMIT_REACHED));

    purchase(1, "over-" + UUID.randomUUID())
        .andExpect(status().isConflict())
        .andExpect(content().json(LIMIT_REACHED, true));
  }

  @Test
  @DisplayName("An invalid purchase is refused before it is forwarded")
  void anInvalidPurchaseIsRefusedBeforeItIsForwarded() throws Exception {
    purchase(0, "zero-" + UUID.randomUUID())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The purchase request is invalid"));
    mockMvc
        .perform(
            post("/api/v1/payments/purchases")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credits\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("The header Idempotency-Key is required"));

    assertThat(SERVICE.received()).isEmpty();
  }

  @Test
  @DisplayName("A payments service that does not answer in time is cut off after the timeout")
  void aPaymentsServiceThatDoesNotAnswerInTimeIsCutOff() throws Exception {
    SERVICE.answer(call -> new Reply(201, confirmed(UUID.randomUUID().toString()), 4_000));

    long startedAt = System.nanoTime();
    purchase(1, "hung-" + UUID.randomUUID())
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "30"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.path").value("/api/v1/payments/purchases"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Credit purchases are temporarily unavailable. The rest of Ayni works as usual."
                        + " If you started a purchase, check your purchases before trying again."));
    // One second of timeout in this test (five by default), not the four the service would take.
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(3_000);
  }

  @Test
  @DisplayName("A payments service that refuses the monolith's secret does not sign the student out")
  void aServiceThatRefusesTheSecretDoesNotSignTheStudentOut() throws Exception {
    SERVICE.answer(
        call ->
            Reply.json(
                401,
                "{\"status\":401,\"message\":\"Only the Ayni backend may call the payments service\"}"));

    mockMvc
        .perform(get("/api/v1/payments/purchases").header("X-Tenant-Id", UPC).header("X-User-Id", student))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().doesNotExist("WWW-Authenticate"));
  }

  private ResultActions purchase(int credits, String key) throws Exception {
    return mockMvc.perform(
        post("/api/v1/payments/purchases")
            .header("X-Tenant-Id", UPC)
            .header("X-User-Id", student)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"credits\":" + credits + "}"));
  }

  private static String confirmed(String id) {
    return """
        {"id":"%s","credits":2,"amount":10.00,"currency":"PEN","status":"CONFIRMED",\
        "providerReference":"simulated-%s","createdAt":"2026-11-25T12:00:00Z",\
        "confirmedAt":"2026-11-25T12:00:01Z","expiresAt":"2026-11-26T12:00:00Z",\
        "providerUnavailable":false,"guidance":null}"""
        .formatted(id, id);
  }
}
