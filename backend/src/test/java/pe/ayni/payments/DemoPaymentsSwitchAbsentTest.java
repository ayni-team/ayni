package pe.ayni.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.payments.application.SwitchSimulatedPaymentsUseCase;

/** Without the {@code demo} profile the switch does not exist, for an administrator either. */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class DemoPaymentsSwitchAbsentTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = PaymentsTestDatabase.INSTANCE;

  private static final String PAYMENTS = "/api/v1/admin/demo/payments";

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext context;
  @Autowired private PlatformAdminRepository admins;
  @Autowired private UserSessionRepository userSessions;
  @Autowired private AccessTokenGenerator tokens;

  @Test
  @DisplayName("Without the demo profile the payment provider cannot be switched")
  void withoutTheDemoProfileThePaymentProviderCannotBeSwitched() throws Exception {
    String administrator =
        PaymentsTestAdministrators.aSignedInAdministrator(admins, userSessions, tokens);

    mockMvc
        .perform(get(PAYMENTS).header("Authorization", "Bearer " + administrator))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            put(PAYMENTS)
                .header("Authorization", "Bearer " + administrator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chargeOutcome\": \"REJECTED\"}"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post(PAYMENTS + "/circuit/reset").header("Authorization", "Bearer " + administrator))
        .andExpect(status().isNotFound());

    assertThat(context.getBeanNamesForType(SwitchSimulatedPaymentsUseCase.class)).isEmpty();
  }
}
