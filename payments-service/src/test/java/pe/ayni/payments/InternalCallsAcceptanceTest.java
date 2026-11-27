package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** Gateway Offloading from the side of the service: only the monolith's calls get in. */
@SpringBootTest
@AutoConfigureMockMvc
class InternalCallsAcceptanceTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("A call without the internal token is refused")
  void aCallWithoutTheInternalTokenIsRefused() throws Exception {
    UUID student = UUID.randomUUID();
    String key = "no-token-" + UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/v1/payments/purchases")
                .header("X-Tenant-Id", "UPC")
                .header("X-User-Id", student)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credits\":1}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("Only the Ayni backend may call the payments service"))
        .andExpect(jsonPath("$.path").value("/api/v1/payments/purchases"));
    mockMvc
        .perform(
            get("/api/v1/payments/purchases")
                .header("X-Internal-Token", "a-guess")
                .header("X-Tenant-Id", "UPC")
                .header("X-User-Id", student))
        .andExpect(status().isUnauthorized());

    assertThat(
            PaymentsTestDatabase.owner()
                .queryForObject(
                    "select count(*) from payments.purchases where idempotency_key = ?",
                    Integer.class,
                    key))
        .isZero();
  }

  @Test
  @DisplayName("The health check needs no token and reports the database")
  void theHealthCheckNeedsNoToken() throws Exception {
    mockMvc
        .perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }
}
