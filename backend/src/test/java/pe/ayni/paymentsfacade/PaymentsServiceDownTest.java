package pe.ayni.paymentsfacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * QA-06 with the payments service down: nothing listens at its address (the test configuration
 * points it at a closed port). Purchases answer a clear 503 at once, and searching, booking and the
 * wallet answer as they always do.
 */
@SpringBootTest(properties = {"spring.mail.host=localhost", "spring.mail.port=3025"})
@AutoConfigureMockMvc
class PaymentsServiceDownTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsFacadeTestDatabase.INSTANCE;

  private static final String UPC = "UPC";
  private final UUID student = UUID.randomUUID();

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("The rest of the platform remains available when the payments service is down")
  void theRestOfThePlatformRemainsAvailableWhenThePaymentsServiceIsDown() throws Exception {
    long startedAt = System.nanoTime();
    mockMvc
        .perform(
            post("/api/v1/payments/purchases")
                .header("X-Tenant-Id", UPC)
                .header("X-User-Id", student)
                .header("Idempotency-Key", "down-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credits\":1}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("Service Unavailable"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Credit purchases are temporarily unavailable. The rest of Ayni works as usual."
                        + " If you started a purchase, check your purchases before trying again."));
    mockMvc
        .perform(get("/api/v1/payments/purchases").header("X-Tenant-Id", UPC).header("X-User-Id", student))
        .andExpect(status().isServiceUnavailable());
    // A closed port refuses at once: the student is not kept waiting for the timeout.
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(5_000);

    mockMvc
        .perform(get("/api/v1/wallet").header("X-Tenant-Id", UPC).header("X-User-Id", student))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(0));
    int bookings =
        mockMvc
            .perform(
                get("/api/v1/bookings/mine").header("X-Tenant-Id", UPC).header("X-User-Id", student))
            .andReturn()
            .getResponse()
            .getStatus();
    Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    int search =
        mockMvc
            .perform(
                get("/api/v1/search/offers")
                    .header("X-Tenant-Id", UPC)
                    .header("X-User-Id", student)
                    .param("catalogItemId", UUID.randomUUID().toString())
                    .param("from", tomorrow.toString())
                    .param("to", tomorrow.plus(4, ChronoUnit.HOURS).toString()))
            .andReturn()
            .getResponse()
            .getStatus();
    // Whatever booking and search answer about a student and a course this database does not have,
    // it is their own answer, not an outage.
    assertThat(bookings).isLessThan(500);
    assertThat(search).isLessThan(500);
  }
}
