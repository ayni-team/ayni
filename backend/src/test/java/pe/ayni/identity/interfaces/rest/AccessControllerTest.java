package pe.ayni.identity.interfaces.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ayni.identity.application.ConfirmAccessResult;
import pe.ayni.identity.application.ConfirmAccessUseCase;
import pe.ayni.identity.application.RequestAccessUseCase;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

class AccessControllerTest {

    private RequestAccessUseCase requestAccess;
    private ConfirmAccessUseCase confirmAccess;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        requestAccess = mock(RequestAccessUseCase.class);
        confirmAccess = mock(ConfirmAccessUseCase.class);

        Clock clock =
                Clock.fixed(
                        Instant.parse("2026-09-22T05:30:00Z"),
                        ZoneOffset.UTC);

        AccessController controller =
                new AccessController(
                        requestAccess,
                        confirmAccess);

        IdentityExceptionHandler handler =
                new IdentityExceptionHandler(clock);

        mvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .setControllerAdvice(handler)
                        .build();
    }

    @Test
    @DisplayName("accepts an institutional access request")
    void acceptsInstitutionalAccessRequest() throws Exception {
        mvc.perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                    {
                      "email": "u202612345@upc.edu.pe"
                    }
                    """))
                .andExpect(status().isAccepted());

        verify(requestAccess)
                .execute("u202612345@upc.edu.pe", "127.0.0.1");
    }

    @Test
    @DisplayName("rejects an invalid email")
    void rejectsInvalidEmail() throws Exception {
        mvc.perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                    {
                      "email": "not-an-email"
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/v1/access/request"));
    }

    @Test
    @DisplayName("returns bad request when the institution is not affiliated")
    void rejectsUnaffiliatedInstitution() throws Exception {
        doThrow(
                new IdentityRuleViolation(
                        "The institution is not affiliated with Ayni"))
                .when(requestAccess)
                .execute(anyString(), anyString());

        mvc.perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                    {
                      "email": "student@gmail.com"
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("The institution is not affiliated with Ayni"))
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/access/request"));
    }

    @Test
    @DisplayName("confirms a link with its token alone and answers the session")
    void confirmsWithTheTokenAlone() throws Exception {
        UUID userId = UUID.randomUUID();
        when(confirmAccess.execute("raw-token"))
                .thenReturn(
                        new ConfirmAccessResult(
                                "session-token",
                                "UPC",
                                userId,
                                Instant.parse("2026-09-29T05:30:00Z")));

        mvc.perform(
                        post("/api/v1/access/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\": \"raw-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("session-token"))
                .andExpect(jsonPath("$.tenantId").value("UPC"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("refuses a confirmation without a token in the common error shape")
    void refusesAConfirmationWithoutToken() throws Exception {
        mvc.perform(
                        post("/api/v1/access/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("token must not be blank"))
                .andExpect(jsonPath("$.path").value("/api/v1/access/confirm"));

        verifyNoInteractions(confirmAccess);
    }
}
