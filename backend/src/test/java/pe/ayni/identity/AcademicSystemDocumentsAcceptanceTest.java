package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.AcademicDocuments;
import pe.ayni.config.SignedInPeople;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.UniversityRegistered;

/**
 * The academic system serves the university and its students (ADR 0006, amended 2026-10-23), over
 * HTTP against a real PostgreSQL with real session tokens (TS03). One test per scenario of {@code
 * features/academic-system-documents.feature} that names this class, with the same display name.
 *
 * <p>The application reads an academic directory of its own, a folder of this test, where each test
 * writes the document of a university with a code nobody else uses (the fixtures of {@link
 * AcademicDocuments}). Registering, activating and importing are the real thing.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@RecordApplicationEvents
class AcademicSystemDocumentsAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final Path DIRECTORY = emptyFolder();

    @DynamicPropertySource
    static void anAcademicDirectoryOfItsOwn(DynamicPropertyRegistry registry) {
        registry.add("ayni.identity.academic-directory", DIRECTORY::toString);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformAdminRepository administrators;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private UserSessionRepository userSessions;
    @Autowired private AccessTokenGenerator tokens;

    private SignedInPeople people;
    private String administrator;

    @BeforeEach
    void anAdministratorSignedIn() {
        people = new SignedInPeople(tenants, users, userSessions, tokens);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        administrators.save(
                new PlatformAdmin(
                        id, "admin-" + id + "@ayni.local", "Administrator", PlatformAdminStatus.ACTIVE, now));
        administrator = tokens.generate().rawToken();
        userSessions.save(
                UserSession.forPlatformAdmin(
                        UUID.randomUUID(), id, tokens.hash(administrator), now.plus(Duration.ofDays(1)), now));
    }

    @Test
    @DisplayName("The administrator registers a university from its academic document")
    void theAdministratorRegistersAUniversityFromItsDocument() throws Exception {
        String code = UniversityRegistrationSupport.newCode();
        document(code, AcademicDocuments.student("nora.quispe@" + domainOf(code), "N1"));

        registerFromDocument(administrator, code, 7, 45)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("Universidad " + code))
                .andExpect(jsonPath("$.logoUrl").value("/logos/" + code.toLowerCase() + ".png"))
                .andExpect(jsonPath("$.primaryColor").value("#123456"))
                .andExpect(jsonPath("$.secondaryColor").value("#FFFFFF"))
                .andExpect(jsonPath("$.emailDomains[0]").value(domainOf(code)))
                .andExpect(jsonPath("$.minimumTeachingGrade").value(14.5))
                .andExpect(jsonPath("$.timezone").value("America/Lima"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.studentCount").value(0))
                .andExpect(jsonPath("$.coordinatorCount").value(0));

        // The baseline policy with the credits the administrator gave, and the same event as the
        // manual registration.
        assertThat(
                        jdbc.queryForMap(
                                "select credits_amount, validity_days from identity.credit_policies"
                                        + " where tenant_id = ? and kind = 'BASELINE' and superseded_at is null",
                                code))
                .containsEntry("credits_amount", 7)
                .containsEntry("validity_days", 45);
        assertThat(events.stream(UniversityRegistered.class).filter(event -> event.tenantId().equals(code)))
                .hasSize(1);
        // The curriculum is not loaded by registering: that is the coordinator's synchronisation.
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from skills.catalog_items where tenant_id = ?",
                                Long.class,
                                code))
                .isZero();
    }

    @Test
    @DisplayName("A code without an academic document is not found")
    void aCodeWithoutADocumentIsNotFound() throws Exception {
        String code = UniversityRegistrationSupport.newCode();

        registerFromDocument(administrator, code, 5, 30)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/admin/universities/from-academic-system"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(tenants.findByCode(code)).isEmpty();
        // A malformed request is still a 400: no code, or credits below 1.
        registerFromDocument(administrator, "", 5, 30).andExpect(status().isBadRequest());
        registerFromDocument(administrator, code, 0, 30).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A university registered twice from its document is a conflict")
    void aUniversityRegisteredTwiceIsAConflict() throws Exception {
        String code = UniversityRegistrationSupport.newCode();
        document(code, AcademicDocuments.student("nora.quispe@" + domainOf(code), "N1"));
        registerFromDocument(administrator, code, 5, 30).andExpect(status().isCreated());
        long published = events.stream(UniversityRegistered.class).count();

        registerFromDocument(administrator, code, 5, 30).andExpect(status().isConflict());

        assertThat(events.stream(UniversityRegistered.class).count()).isEqualTo(published);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from identity.credit_policies where tenant_id = ?",
                                Long.class,
                                code))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Only the platform administrator registers a university from its document")
    void onlyThePlatformAdministratorRegisters() throws Exception {
        String code = UniversityRegistrationSupport.newCode();
        document(code, AcademicDocuments.student("nora.quispe@" + domainOf(code), "N1"));
        String university = people.aUniversity("A");

        registerFromDocument(people.aStudent(university).token(), code, 5, 30)
                .andExpect(status().isForbidden());
        registerFromDocument(people.aCoordinator(university).token(), code, 5, 30)
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/api/v1/admin/universities/from-academic-system")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(code, 5, 30)))
                .andExpect(status().isUnauthorized());

        assertThat(tenants.findByCode(code)).isEmpty();
    }

    @Test
    @DisplayName("A student who is no longer enrolled is not activated and the link stays unused")
    void aStudentNoLongerEnrolledIsNotActivated() throws Exception {
        String code = UniversityRegistrationSupport.newCode();
        String graduate = "graduate@" + domainOf(code);
        String inactive = "inactive@" + domainOf(code);
        document(
                code,
                AcademicDocuments.student(graduate, "G1").replace("\"ACTIVE\"", "\"GRADUATED\""),
                AcademicDocuments.student(inactive, "I1").replace("\"ACTIVE\"", "\"INACTIVE\""));
        registerFromDocument(administrator, code, 5, 30).andExpect(status().isCreated());

        for (String email : new String[] {graduate, inactive}) {
            String token = tokenOf(requestLink(email));

            confirm(token)
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            jsonPath("$.message")
                                    .value("The student is not currently enrolled at the university"));

            assertThat(
                            jdbc.queryForObject(
                                    "select consumed_at is null from identity.access_links where token_hash = ?",
                                    Boolean.class,
                                    tokens.hash(token)))
                    .as(email)
                    .isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from identity.users where tenant_id = ? and email = ?",
                                    Long.class,
                                    code,
                                    email))
                    .isZero();
        }
    }

    @Test
    @DisplayName("Importing the record again updates the cycle")
    void importingTheRecordAgainUpdatesTheCycle() throws Exception {
        String code = UniversityRegistrationSupport.newCode();
        String email = "nora.quispe@" + domainOf(code);
        String nora = AcademicDocuments.student(email, "N1");
        document(code, nora);
        registerFromDocument(administrator, code, 5, 30).andExpect(status().isCreated());

        String opened =
                confirm(tokenOf(requestLink(email)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String session = JsonPath.read(opened, "$.sessionToken");
        String me = read(session, "/api/v1/me");
        assertThat(JsonPath.<Integer>read(me, "$.currentCycle")).isEqualTo(3);
        assertThat(JsonPath.<String>read(me, "$.currentTerm")).isEqualTo("2026-2");

        mockMvc.perform(
                        put("/api/v1/me/profile")
                                .header("Authorization", "Bearer " + session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"photoUrl\": null, \"bio\": null}"))
                .andExpect(status().isOk());

        // A new term at the university: Nora is in cycle 4 and approved one more course.
        document(
                code,
                nora.replace("\"cycle\": 3", "\"cycle\": 4")
                        .replace("\"currentTerm\": \"2026-2\"", "\"currentTerm\": \"2027-1\"")
                        .replace(
                                "\"approvedCourses\": [",
                                "\"approvedCourses\": [{\"courseCode\": \"GEN100\", \"grade\": 17, \"term\": \"2026-2\"},"));
        mockMvc.perform(
                        post("/api/v1/me/academic-record/import").header("Authorization", "Bearer " + session))
                .andExpect(status().isNoContent());

        me = read(session, "/api/v1/me");
        assertThat(JsonPath.<Integer>read(me, "$.currentCycle")).isEqualTo(4);
        assertThat(JsonPath.<String>read(me, "$.currentTerm")).isEqualTo("2027-1");
        assertThat(JsonPath.<String>read(me, "$.career")).isEqualTo("Derecho");
        assertThat(
                        jdbc.queryForList(
                                "select course_name from identity.academic_records where tenant_id = ?"
                                        + " order by course_code",
                                String.class,
                                code))
                .containsExactly("Derecho Civil I", "Lenguaje");
    }

    private ResultActions registerFromDocument(String token, String code, int credits, int days)
            throws Exception {
        return mockMvc.perform(
                post("/api/v1/admin/universities/from-academic-system")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, credits, days)));
    }

    private static String body(String code, int credits, int days) {
        return "{\"code\": \"%s\", \"initialCredits\": %d, \"creditValidityDays\": %d}"
                .formatted(code, credits, days);
    }

    private String requestLink(String email) throws Exception {
        mockMvc.perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        return events.stream(AccessRequested.class)
                .filter(event -> email.equalsIgnoreCase(event.email()))
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

    private String read(String session, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String domainOf(String code) {
        return UniversityRegistrationSupport.domainOf(code);
    }

    private static void document(String code, String... students) {
        try {
            Files.writeString(
                    DIRECTORY.resolve(code + ".json"), AcademicDocuments.document(code, students));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path emptyFolder() {
        try {
            Path folder = Files.createTempDirectory("ayni-academic-documents");
            folder.toFile().deleteOnExit();
            return folder;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
