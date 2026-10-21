package pe.ayni.identity.interfaces.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ayni.identity.application.ConfirmAccessResult;
import pe.ayni.identity.application.ConfirmAccessUseCase;
import pe.ayni.identity.application.FindUniversityByEmailQuery;
import pe.ayni.identity.application.RequestAccessUseCase;
import pe.ayni.identity.application.SignOutUseCase;
import pe.ayni.identity.application.UniversityIdentityView;
import pe.ayni.identity.domain.model.AccessRole;
import pe.ayni.identity.domain.model.IdentityRuleViolation;

class AccessControllerTest {

    private RequestAccessUseCase requestAccess;
    private ConfirmAccessUseCase confirmAccess;
    private FindUniversityByEmailQuery findUniversity;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        requestAccess = mock(RequestAccessUseCase.class);
        confirmAccess = mock(ConfirmAccessUseCase.class);
        findUniversity = mock(FindUniversityByEmailQuery.class);

        Clock clock =
                Clock.fixed(
                        Instant.parse("2026-09-22T05:30:00Z"),
                        ZoneOffset.UTC);

        AccessController controller =
                new AccessController(
                        requestAccess,
                        confirmAccess,
                        mock(SignOutUseCase.class),
                        findUniversity);

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
                                AccessRole.STUDENT,
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

    @Test
    @DisplayName("shows the identity of the university of an email")
    void showsTheIdentityOfTheUniversityOfAnEmail() throws Exception {
        when(findUniversity.execute("u202400001@upc.edu.pe"))
                .thenReturn(
                        new UniversityIdentityView(
                                "UPC", "Universidad Peruana", "https://cdn.ayni.pe/upc.png", "#E30613", "#1A1A1A"));

        mvc.perform(get("/api/v1/access/university").param("email", "u202400001@upc.edu.pe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("UPC"))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.ayni.pe/upc.png"))
                .andExpect(jsonPath("$.primaryColor").value("#E30613"));
    }

    @Test
    @DisplayName("answers 404 in the common error shape when no university claims the email")
    void answersNotFoundWhenNoUniversityClaimsTheEmail() throws Exception {
        when(findUniversity.execute("student@gmail.com"))
                .thenThrow(new NoSuchElementException("The institution is not affiliated with Ayni"));

        mvc.perform(get("/api/v1/access/university").param("email", "student@gmail.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("The institution is not affiliated with Ayni"))
                .andExpect(jsonPath("$.path").value("/api/v1/access/university"));
    }

    @Test
    @DisplayName("answers 400 in the common error shape when the email is missing")
    void answersBadRequestWhenTheEmailIsMissing() throws Exception {
        mvc.perform(get("/api/v1/access/university"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("email is required"))
                .andExpect(jsonPath("$.path").value("/api/v1/access/university"));

        verifyNoInteractions(findUniversity);
    }
}
