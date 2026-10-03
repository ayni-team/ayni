package pe.ayni.identity.interfaces.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ayni.identity.application.UniversityIdentityView;
import pe.ayni.identity.application.UpdateUniversityIdentityUseCase;
import pe.ayni.shared.tenancy.CurrentUser;

class UniversityIdentityControllerTest {

    private UpdateUniversityIdentityUseCase updateIdentity;
    private MockMvc mvc;

    private final UUID coordinatorId =
            UUID.randomUUID();

    @BeforeEach
    void setUp() {

        updateIdentity =
                mock(
                        UpdateUniversityIdentityUseCase.class);

        UniversityIdentityController controller =
                new UniversityIdentityController(
                        updateIdentity);

        IdentityExceptionHandler handler =
                new IdentityExceptionHandler(
                        Clock.fixed(
                                Instant.parse(
                                        "2026-10-02T23:00:00Z"),
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
    void updatesUniversityIdentity() throws Exception {

        when(
                updateIdentity.execute(
                        coordinatorId,
                        "https://cdn.ayni.pe/upc.png",
                        "#D50000",
                        "#FFFFFF"))
                .thenReturn(
                        new UniversityIdentityView(
                                "UPC",
                                "Universidad Peruana de Ciencias Aplicadas",
                                "https://cdn.ayni.pe/upc.png",
                                "#D50000",
                                "#FFFFFF"));

        mvc.perform(
                        put(
                                "/api/v1/coordinator/university-identity")
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "logoUrl": "https://cdn.ayni.pe/upc.png",
                                          "primaryColor": "#D50000",
                                          "secondaryColor": "#FFFFFF"
                                        }
                                        """))
                .andExpect(
                        status().isOk())
                .andExpect(
                        jsonPath("$.code")
                                .value("UPC"))
                .andExpect(
                        jsonPath("$.logoUrl")
                                .value(
                                        "https://cdn.ayni.pe/upc.png"))
                .andExpect(
                        jsonPath("$.primaryColor")
                                .value("#D50000"))
                .andExpect(
                        jsonPath("$.secondaryColor")
                                .value("#FFFFFF"));

        verify(updateIdentity)
                .execute(
                        coordinatorId,
                        "https://cdn.ayni.pe/upc.png",
                        "#D50000",
                        "#FFFFFF");
    }
}