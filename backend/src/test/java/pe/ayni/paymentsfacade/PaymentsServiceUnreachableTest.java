package pe.ayni.paymentsfacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * The service's address never completes the connection: the timeout ends the call before there is
 * one, and the student gets the same 503 as for a slow answer. A stopped container on the Docker
 * network hangs earlier still, resolving its name, and the client then reports a cancelled call
 * instead; that path is covered by the end to end check of the pull request, not here.
 */
@SpringBootTest(
    properties = {
      "spring.mail.host=localhost",
      "spring.mail.port=3025",
      // A blackhole address of a private range: nothing answers, so connecting waits.
      "ayni.payments-service.url=http://10.255.255.1:81",
      "ayni.payments-service.timeout=PT1S"
    })
@AutoConfigureMockMvc
class PaymentsServiceUnreachableTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsFacadeTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("A payments service that cannot be connected to is a 503 after the timeout")
  void aServiceThatCannotBeConnectedToIsA503() throws Exception {
    long startedAt = System.nanoTime();
    mockMvc
        .perform(
            post("/api/v1/payments/purchases")
                .header("X-Tenant-Id", "UPC")
                .header("X-User-Id", UUID.randomUUID())
                .header("Idempotency-Key", "unreachable-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credits\":1}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(503));
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)).isLessThan(4_000);
  }
}
