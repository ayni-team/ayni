package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.identity.UniversityRegistrationSupport.ANA;
import static pe.ayni.identity.UniversityRegistrationSupport.domainOf;
import static pe.ayni.identity.UniversityRegistrationSupport.newCode;
import static pe.ayni.identity.UniversityRegistrationSupport.register;
import static pe.ayni.identity.UniversityRegistrationSupport.registration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;

/**
 * US50 over HTTP against a real PostgreSQL: one test per scenario of {@code
 * features/US50-university-identity.feature}, with the same display name, and the refusals the
 * scenarios do not name.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IdentityTestConfig.class)
class UniversityIdentityAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final UUID ADMINISTRATOR = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000003");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private ImportAcademicRecordUseCase importAcademicRecord;

    @BeforeEach
    void theDemoUniversityAndItsStudents() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        new DemoIdentityData(tenants, users, importAcademicRecord, clock).run(null);
    }

    private String registerUniversity() throws Exception {
        String code = newCode();
        register(mockMvc, ADMINISTRATOR, registration(code, domainOf(code), "13.00", 5, 30))
                .andExpect(status().isCreated());
        return code;
    }

    private UUID store(String tenant, UserRole role, UserStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        users.save(
                new User(
                        id,
                        tenant,
                        role,
                        id + "@" + domainOf(tenant),
                        role == UserRole.STUDENT ? "S" + id.toString().substring(0, 8) : null,
                        "Carla Rojas",
                        null,
                        null,
                        null,
                        null,
                        status,
                        null,
                        status == UserStatus.ACTIVE ? now : null,
                        now,
                        now));
        return id;
    }

    private Map<String, Object> identityOf(String code) {
        return jdbc.queryForMap(
                "select logo_url, primary_color, secondary_color from identity.tenants"
                        + " where code = ?",
                code);
    }

    private ResultActions updateIdentity(String tenant, UUID user, String body) throws Exception {
        return mockMvc.perform(
                put("/api/v1/coordinator/university-identity")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static final String NEW_IDENTITY =
            """
            {
              "logoUrl": "https://cdn.ayni.pe/%s.png",
              "primaryColor": "#003A70",
              "secondaryColor": "#F5F5F5"
            }
            """;

    @Test
    @DisplayName("An active coordinator updates their university identity")
    void anActiveCoordinatorUpdatesTheirUniversityIdentity() throws Exception {

        String code = registerUniversity();
        UUID coordinator = store(code, UserRole.COORDINATOR, UserStatus.ACTIVE);

        updateIdentity(code, coordinator, NEW_IDENTITY.formatted(code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.ayni.pe/" + code.toLowerCase() + ".png"))
                .andExpect(jsonPath("$.primaryColor").value("#003A70"))
                .andExpect(jsonPath("$.secondaryColor").value("#F5F5F5"));

        Map<String, Object> stored = identityOf(code);
        assertThat(stored.get("logo_url")).isEqualTo("https://cdn.ayni.pe/" + code.toLowerCase() + ".png");
        assertThat(stored.get("primary_color")).isEqualTo("#003A70");
        assertThat(stored.get("secondary_color")).isEqualTo("#F5F5F5");
    }

    @Test
    @DisplayName("A student cannot modify university identity")
    void aStudentCannotModifyUniversityIdentity() throws Exception {

        String code = registerUniversity();
        UUID student = store(code, UserRole.STUDENT, UserStatus.ACTIVE);
        Map<String, Object> before = identityOf(code);

        updateIdentity(code, student, NEW_IDENTITY.formatted("student"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(identityOf(code)).isEqualTo(before);

        // Ana is a student of UPC too, and the identity of UPC stays as it was.
        Map<String, Object> upcBefore = identityOf("UPC");
        updateIdentity("UPC", ANA, NEW_IDENTITY.formatted("ana")).andExpect(status().isForbidden());
        assertThat(identityOf("UPC")).isEqualTo(upcBefore);
    }

    @Test
    @DisplayName("A coordinator cannot modify another university")
    void aCoordinatorCannotModifyAnotherUniversity() throws Exception {

        String own = registerUniversity();
        String other = registerUniversity();
        UUID coordinator = store(own, UserRole.COORDINATOR, UserStatus.ACTIVE);
        Map<String, Object> otherBefore = identityOf(other);

        // The request has no field for a university: the one it names is ignored and the
        // university is the one of the request context.
        String naming =
                """
                {
                  "code": "%s",
                  "tenantId": "%s",
                  "logoUrl": "https://cdn.ayni.pe/own.png",
                  "primaryColor": "#111111",
                  "secondaryColor": "#EEEEEE"
                }
                """
                        .formatted(other, other);
        updateIdentity(own, coordinator, naming)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(own));

        assertThat(identityOf(own).get("primary_color")).isEqualTo("#111111");
        assertThat(identityOf(other)).isEqualTo(otherBefore);

        // Asking in the context of the other university: there the coordinator is nobody.
        updateIdentity(other, coordinator, NEW_IDENTITY.formatted("intruder"))
                .andExpect(status().isForbidden());
        assertThat(identityOf(other)).isEqualTo(otherBefore);
    }

    @Test
    @DisplayName("A coordinator who is not active cannot modify university identity")
    void aCoordinatorWhoIsNotActiveCannotModifyUniversityIdentity() throws Exception {

        String code = registerUniversity();
        UUID restricted = store(code, UserRole.COORDINATOR, UserStatus.RESTRICTED);
        Map<String, Object> before = identityOf(code);

        updateIdentity(code, restricted, NEW_IDENTITY.formatted("restricted"))
                .andExpect(status().isForbidden());
        updateIdentity(code, UUID.randomUUID(), NEW_IDENTITY.formatted("nobody"))
                .andExpect(status().isForbidden());

        assertThat(identityOf(code)).isEqualTo(before);
        assertThat(before.get("logo_url")).isNull();
    }
}
