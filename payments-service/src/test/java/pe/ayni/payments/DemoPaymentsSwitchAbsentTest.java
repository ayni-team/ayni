package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.payments.InternalRequests.asMonolith;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.payments.application.SwitchSimulatedPaymentsUseCase;

/** Without the {@code demo} profile the switch does not exist, for the monolith either. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoPaymentsSwitchAbsentTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  private static final String PAYMENTS = "/api/v1/admin/demo/payments";

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("Without the demo profile the payment provider cannot be switched")
  void withoutTheDemoProfileThePaymentProviderCannotBeSwitched() throws Exception {
    UUID administrator = UUID.randomUUID();

    mockMvc
        .perform(asMonolith(get(PAYMENTS), null, administrator))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            asMonolith(put(PAYMENTS), null, administrator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chargeOutcome\": \"REJECTED\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(asMonolith(post(PAYMENTS + "/circuit/reset"), null, administrator))
        .andExpect(status().isNotFound());

    assertThat(context.getBeanNamesForType(SwitchSimulatedPaymentsUseCase.class)).isEmpty();
  }
}
