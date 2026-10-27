package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.AcademicDocuments;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.AccessRequested;

/**
 * TS10, scenario 1, over HTTP against a real PostgreSQL with real session tokens (TS03), with the
 * same name as in {@code features/TS10-register-university.feature}.
 *
 * <p>The application runs with an academic directory of its own, an empty folder when it starts.
 * The administrator registers a university over HTTP and the directory file of that university is
 * written into the folder afterwards, while the same application keeps running: that is what
 * bringing a university in by configuration means, with no code changed and nothing deployed. Its
 * student then asks for access, confirms the link, and is a student like any other.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@RecordApplicationEvents
class OnboardingByConfigurationAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final Path DIRECTORY = emptyFolder();

    @DynamicPropertySource
    static void anAcademicDirectoryOfItsOwn(DynamicPropertyRegistry registry) {
        registry.add("ayni.identity.academic-directory", DIRECTORY::toString);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private PlatformAdminRepository administrators;
    @Autowired private UserSessionRepository userSessions;
    @Autowired private AccessTokenGenerator tokens;

    @Test
    @DisplayName("Onboarding a university by configuration")
    void onboardingAUniversityByConfiguration() throws Exception {
        String administrator = anAdministratorSignedIn();
        String code = UniversityRegistrationSupport.newCode();
        String domain = UniversityRegistrationSupport.domainOf(code);
        // The email does not hold the code: the code is whatever the university's directory says.
        String email = "nora.quispe@" + domain;

        // When the administrator registers a university with its domains and its configuration
        mockMvc
                .perform(
                        post("/api/v1/admin/universities")
                                .header("Authorization", "Bearer " + administrator)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        UniversityRegistrationSupport.registration(
                                                code, domain, "14.00", 6, 120)))
                .andExpect(status().isCreated());

        // Then a student with an email of that university can request access
        String token = tokenOf(requestLink(email));
        // Before its directory is in the folder the academic system does not know the university,
        // and the link is kept for later.
        confirm(token).andExpect(status().is4xxClientError());

        // The university's file arrives in the folder, with the application running.
        Files.writeString(
                DIRECTORY.resolve(code + ".json"),
                AcademicDocuments.document(
                        code, AcademicDocuments.student(email.toUpperCase(), "2026-0042")));

        // And the student signs in and operates as any other student
        String opened = confirm(token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(opened, "$.tenantId")).isEqualTo(code);
        assertThat(JsonPath.<String>read(opened, "$.role")).isEqualTo("STUDENT");
        String session = JsonPath.read(opened, "$.sessionToken");

        String me = as(session, "/api/v1/me");
        assertThat(JsonPath.<String>read(me, "$.fullName")).isEqualTo("Nora Quispe");
        assertThat(JsonPath.<String>read(me, "$.studentCode")).isEqualTo("2026-0042");
        assertThat(JsonPath.<String>read(me, "$.career")).isEqualTo("Derecho");
        // The initial credits of the policy the university was registered with.
        assertThat(JsonPath.<Integer>read(as(session, "/api/v1/wallet"), "$.available")).isEqualTo(6);

        // And no code was changed or deployed: the same running application answered both times.
    }

    private String anAdministratorSignedIn() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        administrators.save(
                new PlatformAdmin(id, "admin-" + id + "@ayni.local", "Administrator", PlatformAdminStatus.ACTIVE, now));
        String token = tokens.generate().rawToken();
        userSessions.save(
                UserSession.forPlatformAdmin(
                        UUID.randomUUID(), id, tokens.hash(token), now.plus(Duration.ofDays(1)), now));
        return token;
    }

    private String requestLink(String email) throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        return events.stream(AccessRequested.class)
                .filter(event -> event.email().equalsIgnoreCase(email))
                .reduce((first, second) -> second)
                .orElseThrow()
                .accessLink();
    }

    private static String tokenOf(String link) {
        return UriComponentsBuilder.fromUriString(link).build().getQueryParams().getFirst("token");
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(
                post("/api/v1/access/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(token)));
    }

    private String as(String session, String path) throws Exception {
        return mockMvc
                .perform(get(path).header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static Path emptyFolder() {
        try {
            Path folder = Files.createTempDirectory("ayni-academic-directory");
            folder.toFile().deleteOnExit();
            return folder;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
