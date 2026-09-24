package pe.ayni.identity.interfaces.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ayni.identity.application.RequestAccessUseCase;
import pe.ayni.identity.domain.model.IdentityRuleViolation;
import pe.ayni.identity.application.ConfirmAccessUseCase;
class AccessControllerTest {

    private RequestAccessUseCase requestAccess;
    private MockMvc mvc;
    private ConfirmAccessUseCase confirmAccess;
    @BeforeEach
    void setUp() {
        requestAccess = mock(RequestAccessUseCase.class);

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
        confirmAccess = mock(ConfirmAccessUseCase.class);
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
}