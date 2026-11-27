package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.payments.InternalRequests.asMonolith;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.payments.application.PaymentProvider;
import pe.ayni.payments.application.PaymentProviderGateway;

/**
 * TS06 exercises the real resilience decorator over HTTP; scenarios map to the methods below and
 * {@link pe.ayni.payments.infrastructure.ResilientPaymentProviderTest}. The values are shortened
 * here so the test does not wait 30 seconds; the defaults are the ones of ADR 0008.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "ayni.payments.resilience.failure-threshold=1",
      "ayni.payments.resilience.open-duration=PT3S",
      "ayni.payments.resilience.timeout=PT0.1S",
      "ayni.payments.resilience.max-concurrent-calls=1"
    })
class PaymentProviderResilienceAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  private static final String UPC = "UPC";

  /** Far longer than a busy build machine needs, far shorter than a provider that never answers. */
  private static final long UNANSWERED_MILLIS = 5_000;

  private final UUID student = UUID.randomUUID();
  private final AtomicInteger providerAttempts = new AtomicInteger();
  private final CountDownLatch releaseSlowProvider = new CountDownLatch(1);
  private final JdbcTemplate owner = PaymentsTestDatabase.owner();

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PaymentProviderGateway gateway;

  @BeforeEach
  void aSlowProvider() {
    when(gateway.charge(any(), any(BigDecimal.class), any()))
        .thenAnswer(
            invocation -> {
              providerAttempts.incrementAndGet();
              // Slow until the test releases it, whichever attempt this is: on a busy machine the
              // first attempt may be cancelled by the timeout before it reaches the provider.
              if (releaseSlowProvider.getCount() > 0) {
                awaitIgnoringInterrupt(releaseSlowProvider);
                return new PaymentProvider.PaymentResult(
                    PaymentProvider.PaymentResult.Outcome.PENDING, "late-response");
              }
              return new PaymentProvider.PaymentResult(
                  PaymentProvider.PaymentResult.Outcome.CONFIRMED, "recovered");
            });
  }

  @Test
  @DisplayName("Free platform operations remain available")
  void timesOutAndRejectsPurchaseCallsWhileKeepingTheListAvailableAndRecovering()
      throws Exception {
    try {
      long startedAt = System.nanoTime();
      purchase("slow-" + UUID.randomUUID())
          .andExpect(status().isAccepted())
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.providerUnavailable").value(true))
          .andExpect(
              jsonPath("$.guidance")
                  .value(
                      "Credit purchases are temporarily unavailable. This attempt remains pending; do not retry. Check this purchase's status."));
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      // The provider does not answer until it is released below, so answering at all is the
      // timeout; the bound tells that apart from waiting forever, not how fast the machine is.
      assertThat(elapsedMillis).isLessThan(UNANSWERED_MILLIS);
      int attemptsOfTheFirstPurchase = providerAttempts.get();

      long openCircuitStartedAt = System.nanoTime();
      purchase("open-" + UUID.randomUUID())
          .andExpect(status().isAccepted())
          .andExpect(jsonPath("$.status").value("PENDING"))
          .andExpect(jsonPath("$.providerUnavailable").value(true));
      assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - openCircuitStartedAt))
          .isLessThan(UNANSWERED_MILLIS);
      // The open circuit refused it without asking the provider.
      assertThat(attemptsOfTheFirstPurchase).isLessThanOrEqualTo(1);
      assertThat(providerAttempts).hasValue(attemptsOfTheFirstPurchase);

      mockMvc
          .perform(asMonolith(get("/api/v1/payments/purchases"), UPC, student))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].status").value("PENDING"));
      assertThat(creditsOwed()).isZero();

      releaseSlowProvider.countDown();
      Thread.sleep(3100);
      purchase("recovered-" + UUID.randomUUID())
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.status").value("CONFIRMED"))
          .andExpect(jsonPath("$.providerUnavailable").value(false));
      assertThat(creditsOwed()).isEqualTo(1);
    } finally {
      releaseSlowProvider.countDown();
    }
  }

  private ResultActions purchase(String idempotencyKey) throws Exception {
    return mockMvc.perform(
        asMonolith(post("/api/v1/payments/purchases"), UPC, student)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"credits\":1}"));
  }

  private int creditsOwed() {
    return owner.queryForObject(
        "select coalesce(sum((payload->>'credits')::int), 0) from payments.outbox"
            + " where payload->>'studentId' = ?",
        Integer.class,
        student.toString());
  }

  private static void awaitIgnoringInterrupt(CountDownLatch latch) {
    boolean interrupted = false;
    while (latch.getCount() > 0) {
      try {
        latch.await();
      } catch (InterruptedException ignored) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
