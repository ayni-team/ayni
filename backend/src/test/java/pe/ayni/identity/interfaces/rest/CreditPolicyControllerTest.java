package pe.ayni.identity.interfaces.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ayni.identity.PolicyKind;
import pe.ayni.identity.application.CreditPolicyConfigurationView;
import pe.ayni.identity.application.UpdateCreditPolicyUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

class CreditPolicyControllerTest {

    private UpdateCreditPolicyUseCase updateCreditPolicy;
    private MockMvc mvc;

    private final UUID coordinatorId =
            UUID.randomUUID();

    @BeforeEach
    void setUp() {

        updateCreditPolicy =
                mock(
                        UpdateCreditPolicyUseCase.class);

        CreditPolicyController controller =
                new CreditPolicyController(
                        updateCreditPolicy);

        IdentityExceptionHandler handler =
                new IdentityExceptionHandler(
                        Clock.fixed(
                                Instant.parse(
                                        "2026-10-03T01:00:00Z"),
                                ZoneOffset.UTC));

        mvc =
                MockMvcBuilders
                        .standaloneSetup(
                                controller)
                        .setControllerAdvice(
                                handler)
                        .build();

        CurrentUser.set(
                coordinatorId);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void supersedesBaselinePolicy() throws Exception {

        UUID policyId =
                UUID.randomUUID();

        when(
                updateCreditPolicy.execute(
                        coordinatorId,
                        8,
                        45))
                .thenReturn(
                        new CreditPolicyConfigurationView(
                                policyId,
                                PolicyKind.BASELINE,
                                8,
                                45,
                                LocalDate.of(
                                        2026,
                                        10,
                                        2)));

        mvc.perform(
                        put(
                                "/api/v1/coordinator/credit-policy")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "creditsAmount": 8,
                                          "validityDays": 45
                                        }
                                        """))
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(
                                        policyId.toString()))
                .andExpect(
                        jsonPath("$.kind")
                                .value("BASELINE"))
                .andExpect(
                        jsonPath("$.creditsAmount")
                                .value(8))
                .andExpect(
                        jsonPath("$.validityDays")
                                .value(45))
                .andExpect(
                        jsonPath("$.validFrom")
                                .value(
                                        "2026-10-02"));

        verify(updateCreditPolicy)
                .execute(
                        coordinatorId,
                        8,
                        45);
    }

    @Test
    void rejectsInvalidPolicyValues() throws Exception {

        mvc.perform(
                        put(
                                "/api/v1/coordinator/credit-policy")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "creditsAmount": 0,
                                          "validityDays": 0
                                        }
                                        """))
                .andExpect(
                        status().isBadRequest());
    }
}