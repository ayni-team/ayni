package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * Acting as a persona under the {@code demo} profile, over HTTP and against a real PostgreSQL, with
 * the platform administrator's real session token. One test per scenario of {@code
 * features/demo-personas.feature}, with the same display name.
 *
 * <p>The database is shared with the other tests of identity, so each test creates a university of
 * its own and reads only what belongs to it.
 */
@SpringBootTest(
    properties = {
        "ayni.test.identity-headers=false",
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "ayni.wallet.expiry-cron=-",
        "ayni.booking.generation-cron=-",
        "ayni.booking.hold-release-delay=PT24H",
        "ayni.sessions.presence-check-delay=PT24H",
        "ayni.sessions.closing-delay=PT24H",
        "ayni.sessions.no-show-delay=PT24H",
        "ayni.audit.analysis-delay=PT24H",
        "ayni.payments.reconciliation-delay=PT24H"
    })
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoPersonasAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final String PERSONAS = "/api/v1/admin/demo/personas";
    private static final String LOGIN_AS = "/api/v1/admin/demo/login-as";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private AccessTokenGenerator tokens;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private UserSessionRepository sessions;
    @Autowired private PlatformAdminRepository admins;

    private SignedInPeople people;
    private String administrator;

    @BeforeEach
    void anAdministratorSignedIn() {
        people = new SignedInPeople(tenants, users, sessions, tokens);
        UUID adminId = UUID.randomUUID();
        Instant now = Instant.now();
        admins.save(
                new PlatformAdmin(
                        adminId,
                        "admin-" + adminId + "@ayni.test",
                        "Admin",
                        PlatformAdminStatus.ACTIVE,
                        now));
        administrator = tokens.generate().rawToken();
        // A year, as the sessions of the demo profile last: a month would expire one of thirty days.
        sessions.save(
                UserSession.forPlatformAdmin(
                        UUID.randomUUID(),
                        adminId,
                        tokens.hash(administrator),
                        now.plus(Duration.ofDays(365)),
                        now));
    }

    private static MockHttpServletRequestBuilder bearer(
            MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private ResultActions loginAs(String token, String body) throws Exception {
        return mockMvc.perform(
                bearer(post(LOGIN_AS), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions loginAsEmail(String token, String email) throws Exception {
        return loginAs(token, "{\"email\": \"%s\"}".formatted(email));
    }

    /** A person of the university that exists in the table, with the name and status given. */
    private User person(
            String tenant,
            UserRole role,
            String fullName,
            String career,
            UserStatus status,
            OnboardingStep step) {

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return users.save(
                new User(
                        id,
                        tenant,
                        role,
                        id + "@" + tenant.toLowerCase() + ".edu.pe",
                        role == UserRole.STUDENT ? "S" + id.toString().substring(0, 8) : null,
                        fullName,
                        career,
                        null,
                        null,
                        null,
                        status,
                        step,
                        status == UserStatus.ACTIVE ? now : null,
                        now,
                        now));
    }

    private String personas() throws Exception {
        MvcResult result =
                mockMvc
                        .perform(bearer(get(PERSONAS), administrator))
                        .andExpect(status().isOk())
                        .andReturn();
        return result.getResponse().getContentAsString();
    }

    private List<Map<String, Object>> personasOf(String tenant) throws Exception {
        return JsonPath.read(personas(), "$[?(@.universityCode == '" + tenant + "')]");
    }

    @Test
    @DisplayName("An active person is listed and one who has not activated is not")
    void anActivePersonIsListedAndOneWhoHasNotActivatedIsNot() throws Exception {
        String tenant = people.aUniversity("DP");
        User active =
                person(tenant, UserRole.STUDENT, "Ada Active", "Software Engineering",
                        UserStatus.ACTIVE, OnboardingStep.COMPLETED);
        User pending =
                person(tenant, UserRole.COORDINATOR, "Pat Pending", null,
                        UserStatus.PENDING, null);
        User restricted =
                person(tenant, UserRole.STUDENT, "Rex Restricted", null,
                        UserStatus.RESTRICTED, OnboardingStep.PROFILE);

        List<Map<String, Object>> listed = personasOf(tenant);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0))
                .containsEntry("email", active.getEmail())
                .containsEntry("fullName", "Ada Active")
                .containsEntry("role", "STUDENT")
                .containsEntry("universityCode", tenant)
                .containsEntry("universityName", "University " + tenant)
                .containsEntry("career", "Software Engineering")
                .containsEntry("onboardingStep", "COMPLETED");
        assertThat(personas())
                .doesNotContain(pending.getEmail())
                .doesNotContain(restricted.getEmail());
    }

    @Test
    @DisplayName("The people of every university are listed by university, role and name")
    void thePeopleOfEveryUniversityAreListedByUniversityRoleAndName() throws Exception {
        String first = people.aUniversity("DP");
        String second = people.aUniversity("DP");
        person(first, UserRole.STUDENT, "Zoe Zapata", null, UserStatus.ACTIVE, OnboardingStep.PROFILE);
        person(first, UserRole.STUDENT, "Ana Alva", null, UserStatus.ACTIVE, OnboardingStep.PROFILE);
        person(first, UserRole.COORDINATOR, "Mia Mora", null, UserStatus.ACTIVE, null);
        person(second, UserRole.STUDENT, "Luz Lara", null, UserStatus.ACTIVE, OnboardingStep.PROFILE);

        List<Map<String, Object>> everybody = JsonPath.read(personas(), "$");

        assertThat(everybody)
                .extracting(person -> (String) person.get("universityCode"))
                .contains(first, second)
                .isSorted();
        assertThat(personasOf(first))
                .extracting(person -> person.get("role") + " " + person.get("fullName"))
                .containsExactly("COORDINATOR Mia Mora", "STUDENT Ana Alva", "STUDENT Zoe Zapata");
        // Both universities are listed in the same answer, each one's people as itself.
        assertThat(personasOf(second)).extracting(person -> person.get("fullName")).containsExactly("Luz Lara");
    }

    @Test
    @DisplayName("Acting as a person opens a session that answers as that person")
    void actingAsAPersonOpensASessionThatAnswersAsThatPerson() throws Exception {
        String tenant = people.aUniversity("DP");
        User student =
                person(tenant, UserRole.STUDENT, "Ada Active", null, UserStatus.ACTIVE,
                        OnboardingStep.COMPLETED);

        MvcResult opened =
                loginAsEmail(administrator, student.getEmail())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tenantId").value(tenant))
                        .andExpect(jsonPath("$.userId").value(student.getId().toString()))
                        .andExpect(jsonPath("$.role").value("STUDENT"))
                        .andExpect(jsonPath("$.sessionToken").isNotEmpty())
                        .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                        .andReturn();
        String token = JsonPath.read(opened.getResponse().getContentAsString(), "$.sessionToken");

        mockMvc
                .perform(bearer(get("/api/v1/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student.getId().toString()))
                .andExpect(jsonPath("$.email").value(student.getEmail()))
                .andExpect(jsonPath("$.fullName").value("Ada Active"));
    }

    @Test
    @DisplayName("A coordinator can be acted as too, whatever the case of the email")
    void aCoordinatorCanBeActedAsTooWhateverTheCaseOfTheEmail() throws Exception {
        String tenant = people.aUniversity("DP");
        User coordinator =
                person(tenant, UserRole.COORDINATOR, "Mia Mora", null, UserStatus.ACTIVE, null);

        loginAsEmail(administrator, coordinator.getEmail().toUpperCase())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(coordinator.getId().toString()))
                .andExpect(jsonPath("$.role").value("COORDINATOR"));
    }

    @Test
    @DisplayName("The session of a persona lasts as long as one opened by a link and stores only a hash")
    void theSessionOfAPersonaLastsAsLongAsOneOpenedByALinkAndStoresOnlyAHash() throws Exception {
        String tenant = people.aUniversity("DP");
        User student =
                person(tenant, UserRole.STUDENT, "Ada Active", null, UserStatus.ACTIVE,
                        OnboardingStep.COMPLETED);

        Instant before = clock.instant();
        MvcResult opened =
                loginAsEmail(administrator, student.getEmail()).andExpect(status().isOk()).andReturn();
        Instant after = clock.instant();
        String body = opened.getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.sessionToken");

        // The duration the application says a session has: a year under the demo profile.
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(expiresAt)
                .isBetween(before.plus(Duration.ofDays(365)), after.plus(Duration.ofDays(365)));

        Map<String, Object> row =
                jdbc.queryForMap(
                        "select token_hash, tenant_id, user_id, admin_id from identity.user_sessions"
                                + " where token_hash = ?",
                        tokens.hash(token));
        assertThat(row.get("token_hash")).isNotEqualTo(token);
        assertThat(row.get("tenant_id")).isEqualTo(tenant);
        assertThat(row.get("user_id")).isEqualTo(student.getId());
        assertThat(row.get("admin_id")).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from identity.user_sessions where token_hash = ?",
                                Long.class,
                                token))
                .as("the clear token is nowhere in the table")
                .isZero();
    }

    @Test
    @DisplayName("Acting as a person consumes no access link and writes no notice")
    void actingAsAPersonConsumesNoAccessLinkAndWritesNoNotice() throws Exception {
        String tenant = people.aUniversity("DP");
        User student =
                person(tenant, UserRole.STUDENT, "Ada Active", null, UserStatus.ACTIVE,
                        OnboardingStep.COMPLETED);
        long linksBefore = count("identity.access_links");
        long consumedBefore = count("identity.access_links where consumed_at is not null");
        long noticesBefore = count("notifications.notifications");

        loginAsEmail(administrator, student.getEmail()).andExpect(status().isOk());

        assertThat(count("identity.access_links")).isEqualTo(linksBefore);
        assertThat(count("identity.access_links where consumed_at is not null"))
                .isEqualTo(consumedBefore);
        assertThat(count("notifications.notifications")).isEqualTo(noticesBefore);
    }

    private long count(String tableAndFilter) {
        return jdbc.queryForObject("select count(*) from " + tableAndFilter, Long.class);
    }

    @Test
    @DisplayName("A student is refused on both routes")
    void aStudentIsRefusedOnBothRoutes() throws Exception {
        String tenant = people.aUniversity("DP");
        Person student = people.aStudent(tenant);
        User someone =
                person(tenant, UserRole.STUDENT, "Ada Active", null, UserStatus.ACTIVE,
                        OnboardingStep.COMPLETED);

        mockMvc.perform(bearer(get(PERSONAS), student.token())).andExpect(status().isForbidden());
        loginAsEmail(student.token(), someone.getEmail()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("An email nobody has is not found")
    void anEmailNobodyHasIsNotFound() throws Exception {
        loginAsEmail(administrator, "nobody@nowhere.example")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value(LOGIN_AS))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("nobody@nowhere.example"))));
    }

    @Test
    @DisplayName("A person who has not activated cannot be acted as")
    void aPersonWhoHasNotActivatedCannotBeActedAs() throws Exception {
        String tenant = people.aUniversity("DP");
        User pending =
                person(tenant, UserRole.COORDINATOR, "Pat Pending", null, UserStatus.PENDING, null);
        long sessionsBefore = count("identity.user_sessions");

        loginAsEmail(administrator, pending.getEmail()).andExpect(status().isNotFound());

        assertThat(count("identity.user_sessions")).isEqualTo(sessionsBefore);
    }

    @Test
    @DisplayName("A malformed email is refused")
    void aMalformedEmailIsRefused() throws Exception {
        loginAs(administrator, "{\"email\": \"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        loginAs(administrator, "{\"email\": \"  \"}").andExpect(status().isBadRequest());
        loginAs(administrator, "{}").andExpect(status().isBadRequest());
        loginAs(administrator, "").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Without a session both routes answer 401")
    void withoutASessionBothRoutesAnswer401() throws Exception {
        mockMvc.perform(get(PERSONAS)).andExpect(status().isUnauthorized());
        mockMvc
                .perform(
                        post(LOGIN_AS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"a@b.example\"}"))
                .andExpect(status().isUnauthorized());
    }
}
