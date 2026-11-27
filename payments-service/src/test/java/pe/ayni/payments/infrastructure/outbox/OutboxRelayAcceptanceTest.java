package pe.ayni.payments.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.payments.PaymentsTestDatabase;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.application.PurchaseCreditsUseCase;
import pe.ayni.payments.tenancy.TenantContext;

/**
 * The relay against a real PostgreSQL and a fake monolith on a local port: what the monolith
 * receives, with which secret, and what happens when it does not accept. Scenarios of {@code
 * features/outbox-and-internal-calls.feature}.
 */
@SpringBootTest(
    properties = {
      "ayni.payments.outbox.backoff-initial=PT0.2S",
      "ayni.payments.outbox.backoff-max=PT1S",
      "ayni.payments.outbox.timeout=PT1S"
    })
class OutboxRelayAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  private static final HttpServer MONOLITH = fakeMonolith();
  private static final List<Map<String, String>> RECEIVED = new CopyOnWriteArrayList<>();
  private static final AtomicInteger ANSWER = new AtomicInteger(202);

  @DynamicPropertySource
  static void theFakeMonolith(DynamicPropertyRegistry registry) {
    registry.add(
        "ayni.payments.monolith-url",
        () -> "http://127.0.0.1:" + MONOLITH.getAddress().getPort());
  }

  @AfterAll
  static void stopTheFakeMonolith() {
    MONOLITH.stop(0);
  }

  private final JdbcTemplate owner = PaymentsTestDatabase.owner();
  private final ObjectMapper json = new ObjectMapper();
  private final UUID student = UUID.randomUUID();

  @Autowired private OutboxRelay relay;
  @Autowired private PurchaseCreditsUseCase purchaseCredits;
  @MockitoBean private PaymentProvider provider;

  @BeforeEach
  void aProviderThatConfirmsAndAMonolithThatAccepts() {
    when(provider.charge(any(), any(BigDecimal.class), any()))
        .thenAnswer(
            invocation ->
                new PaymentProvider.PaymentResult(
                    PaymentProvider.PaymentResult.Outcome.CONFIRMED,
                    "test-" + invocation.getArgument(0)));
    ANSWER.set(202);
    // Whatever earlier tests of the shared database left unsent is delivered first, so each test
    // counts only its own.
    while (relay.relay() > 0) {
      // drained
    }
    RECEIVED.clear();
  }

  @Test
  @DisplayName("A confirmed purchase is delivered to the monolith with the internal token")
  void aConfirmedPurchaseIsDeliveredToTheMonolith() throws Exception {
    UUID purchaseId = aConfirmedPurchase(3);

    assertThat(relay.relay()).isEqualTo(1);

    assertThat(RECEIVED).hasSize(1);
    Map<String, String> request = RECEIVED.get(0);
    assertThat(request)
        .containsEntry("method", "POST")
        .containsEntry("path", "/internal/v1/events/purchase-confirmed")
        .containsEntry("token", "test-internal-token")
        .containsEntry("contentType", "application/json");
    JsonNode event = json.readTree(request.get("body"));
    assertThat(event.get("purchaseId").asText()).isEqualTo(purchaseId.toString());
    assertThat(event.get("tenantId").asText()).isEqualTo("UPC");
    assertThat(event.get("studentId").asText()).isEqualTo(student.toString());
    assertThat(event.get("credits").asInt()).isEqualTo(3);
    assertThat(event.get("eventId").asText()).isNotBlank();
    assertThat(Instant.parse(event.get("occurredOn").asText())).isNotNull();

    assertThat(sentAt(purchaseId)).isNotNull();
    assertThat(relay.relay()).isZero();
    assertThat(RECEIVED).hasSize(1);
  }

  @Test
  @DisplayName("A monolith that does not accept the event is tried again later")
  void aMonolithThatDoesNotAcceptIsTriedAgainLater() throws Exception {
    ANSWER.set(503);
    UUID purchaseId = aConfirmedPurchase(1);

    assertThat(relay.relay()).isZero();

    Map<String, Object> row =
        owner.queryForMap(
            "select attempts, sent_at, next_attempt_at > now() as later, last_error"
                + " from payments.outbox where aggregate_id = ?",
            purchaseId);
    assertThat(row).containsEntry("attempts", 1).containsEntry("sent_at", null);
    assertThat(row.get("later")).isEqualTo(true);
    assertThat((String) row.get("last_error")).contains("503");
    // Not due yet: nothing is sent before the wait is over.
    assertThat(relay.relay()).isZero();

    ANSWER.set(202);
    Thread.sleep(300);
    assertThat(relay.relay()).isEqualTo(1);
    assertThat(sentAt(purchaseId)).isNotNull();
    assertThat(RECEIVED).hasSize(2);
  }

  private UUID aConfirmedPurchase(int credits) {
    return TenantContext.callAs(
        "UPC",
        () -> purchaseCredits.execute(student, credits, "outbox-" + UUID.randomUUID()).id());
  }

  private Object sentAt(UUID purchaseId) {
    return owner.queryForObject(
        "select sent_at from payments.outbox where aggregate_id = ?", Object.class, purchaseId);
  }

  private static HttpServer fakeMonolith() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            RECEIVED.add(
                Map.of(
                    "method", exchange.getRequestMethod(),
                    "path", exchange.getRequestURI().getPath(),
                    "token", String.valueOf(exchange.getRequestHeaders().getFirst("X-Internal-Token")),
                    "contentType",
                        String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")),
                    "body", body));
            exchange.sendResponseHeaders(ANSWER.get(), -1);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
