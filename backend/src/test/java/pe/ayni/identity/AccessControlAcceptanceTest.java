package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.application.GetMyProfileQuery;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.TenantStatus;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.AccessRequested;

/**
 * TS03 over HTTP against a real PostgreSQL, through the real filter and with real session tokens.
 * One test per scenario of {@code features/TS03-access-control.feature}, with the same name.
 *
 * <p>{@code ayni.test.identity-headers=false} turns off the test filter that keeps {@code
 * X-Tenant-Id} and {@code X-User-Id} working for the scenarios of the other modules, so here those
 * headers are what they are in the application: ignored.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(IdentityTestConfig.class)
class AccessControlAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    /** Ana, of {@code DemoIdentityData}: an active student of UPC. */
    private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");

    /** Bruno, of {@code DemoIdentityData}: another student of UPC. */
    private static final UUID BRUNO = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final String ANAS_EMAIL = "u202400001@upc.edu.pe";

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private AccessTokenGenerator tokens;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private UserSessionRepository sessions;
    @Autowired private PlatformAdminRepository admins;
    @Autowired private ImportAcademicRecordUseCase importAcademicRecord;

    @MockitoSpyBean private GetMyProfileQuery getMyProfile;

    @BeforeEach
    void theDemoUniversityAndItsStudents() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        new DemoIdentityData(tenants, users, importAcademicRecord, clock).run(null);
    }

    /** Opens a session straight in the table and returns its token, as confirming a link would. */
    private String sessionOf(String tenant, UUID userId, Duration lasting) {
        String token = tokens.generate().rawToken();
        Instant now = clock.instant();
        sessions.save(
                new UserSession(
                        UUID.randomUUID(), tenant, userId, tokens.hash(token), now.plus(lasting), now));
        return token;
    }

    private static MockHttpServletRequestBuilder bearer(
            MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String bioOf(UUID userId) {
        return jdbc.queryForObject(
                "select coalesce(bio, '') from identity.users where id = ?", String.class, userId);
    }

    private String tokenOfLinkFor(String email) throws Exception {
        mockMvc.perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        String link =
                events.stream(AccessRequested.class)
                        .filter(event -> event.email().equals(email))
                        .reduce((first, second) -> second)
                        .orElseThrow()
                        .accessLink();
        return UriComponentsBuilder.fromUriString(link).build().getQueryParams().getFirst("token");
    }

    private ResultActions confirm(String linkToken) throws Exception {
        return mockMvc.perform(
                post("/api/v1/access/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(linkToken)));
    }

    private ResultActions changeBio(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(
                request.contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Changed by a request that must not run\"}"));
    }

    private Tenant aNewUniversity() {
        String code = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return tenants.save(
                new Tenant(
                        UUID.randomUUID(),
                        code,
                        "University " + code,
                        null,
                        "#111111",
                        "#222222",
                        List.of(code.toLowerCase() + ".edu.pe"),
                        new BigDecimal("13.00"),
                        ZoneId.of("America/Lima"),
                        TenantStatus.ACTIVE,
                        clock.instant()));
    }

    private UUID aCoordinatorOf(Tenant tenant) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        users.save(
                new User(
                        id,
                        tenant.getCode(),
                        pe.ayni.identity.UserRole.COORDINATOR,
                        id + "@" + tenant.getEmailDomains().get(0),
                        null,
                        "Carla Rojas",
                        null,
                        null,
                        null,
                        null,
                        UserStatus.ACTIVE,
                        null,
                        now,
                        now,
                        now));
        return id;
    }

    @Test
    @DisplayName("A request without credentials is answered 401 and executes nothing")
    void aRequestWithoutCredentialsIsAnswered401() throws Exception {
        String before = bioOf(ANA);

        changeBio(put("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/v1/me/profile"))
                .andExpect(jsonPath("$.timestamp").exists());

        // The headers the application used to trust open nothing now.
        changeBio(
                        put("/api/v1/me/profile")
                                .header("X-Tenant-Id", "UPC")
                                .header("X-User-Id", ANA.toString()))
                .andExpect(status().isUnauthorized());

        // Nor does a token nobody issued, or an Authorization that is not a bearer token.
        changeBio(bearer(put("/api/v1/me/profile"), "a-token-nobody-issued"))
                .andExpect(status().isUnauthorized());
        changeBio(put("/api/v1/me/profile").header("Authorization", "Basic YW5hOmFuYQ=="))
                .andExpect(status().isUnauthorized());

        assertThat(bioOf(ANA)).isEqualTo(before);
    }

    @Test
    @DisplayName("A role that is not enough is answered 403 and executes nothing")
    void aRoleThatIsNotEnoughIsAnswered403() throws Exception {
        String ana = sessionOf("UPC", ANA, Duration.ofDays(7));
        String colourBefore =
                jdbc.queryForObject(
                        "select primary_color from identity.tenants where code = 'UPC'", String.class);

        mockMvc.perform(
                        bearer(put("/api/v1/coordinator/university-identity"), ana)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primaryColor\": \"#000000\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("This route is for COORDINATOR"));

        String code = "X" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        mockMvc.perform(
                        bearer(post("/api/v1/admin/universities"), ana)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"code": "%s", "name": "Not to be registered",
                                         "emailDomains": ["%s.edu.pe"], "minimumTeachingGrade": 13,
                                         "timezone": "America/Lima", "initialCredits": 5,
                                         "initialCreditValidityDays": 30}
                                        """
                                                .formatted(code, code.toLowerCase())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This route is for PLATFORM_ADMIN"));

        assertThat(
                        jdbc.queryForObject(
                                "select primary_color from identity.tenants where code = 'UPC'",
                                String.class))
                .isEqualTo(colourBefore);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from identity.tenants where code = ?",
                                Integer.class,
                                code))
                .isZero();
    }

    @Test
    @DisplayName("An expired token is refused without reading any data")
    void anExpiredTokenIsRefusedWithoutReadingAnyData() throws Exception {
        String ana = sessionOf("UPC", ANA, Duration.ofMinutes(1));
        mockMvc.perform(bearer(get("/api/v1/me"), ana)).andExpect(status().isOk());

        // The moment it expires it opens nothing: expiry is exclusive.
        clock.advance(Duration.ofMinutes(1));

        org.mockito.Mockito.clearInvocations(getMyProfile);
        mockMvc.perform(bearer(get("/api/v1/me"), ana))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The session expired. Sign in again"));
        verify(getMyProfile, never()).execute(any());
    }

    @Test
    @DisplayName("A revoked token is refused without reading any data")
    void aRevokedTokenIsRefusedWithoutReadingAnyData() throws Exception {
        String ana = sessionOf("UPC", ANA, Duration.ofDays(7));
        mockMvc.perform(bearer(post("/api/v1/access/logout"), ana))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.clearInvocations(getMyProfile);
        mockMvc.perform(bearer(get("/api/v1/me"), ana))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The session was closed. Sign in again"));
        verify(getMyProfile, never()).execute(any());
    }

    @Test
    @DisplayName("The university and the person of a request are the ones of its session")
    void theUniversityAndThePersonAreTheOnesOfItsSession() throws Exception {
        String session =
                JsonPath.read(
                        confirm(tokenOfLinkFor(ANAS_EMAIL))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.role").value("STUDENT"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.sessionToken");

        mockMvc.perform(
                        bearer(get("/api/v1/me"), session)
                                .header("X-Tenant-Id", "PUCP")
                                .header("X-User-Id", BRUNO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ANA.toString()))
                .andExpect(jsonPath("$.tenantId").value("UPC"));
    }

    @Test
    @DisplayName("Signing out closes the session")
    void signingOutClosesTheSession() throws Exception {
        String ana = sessionOf("UPC", ANA, Duration.ofDays(7));

        mockMvc.perform(bearer(post("/api/v1/access/logout"), ana))
                .andExpect(status().isNoContent());

        assertThat(
                        jdbc.queryForObject(
                                "select revoked_at is not null from identity.user_sessions"
                                        + " where token_hash = ?",
                                Boolean.class,
                                tokens.hash(ana)))
                .isTrue();
        mockMvc.perform(bearer(get("/api/v1/me"), ana)).andExpect(status().isUnauthorized());
        // Signing out with the closed token is refused like any other request with it.
        mockMvc.perform(bearer(post("/api/v1/access/logout"), ana))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/access/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A platform administrator signs in by link and has no university")
    void aPlatformAdministratorSignsInByLink() throws Exception {
        UUID adminId = UUID.randomUUID();
        String email = "admin-" + adminId + "@ayni.test";
        admins.save(
                new PlatformAdmin(
                        adminId, email, "Admin", PlatformAdminStatus.ACTIVE, clock.instant()));

        String linkToken = tokenOfLinkFor(email);
        assertThat(
                        events.stream(AccessRequested.class)
                                .filter(event -> event.email().equals(email))
                                .findFirst()
                                .orElseThrow()
                                .tenantId())
                .isNull();

        String session =
                JsonPath.read(
                        confirm(linkToken)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"))
                                .andExpect(jsonPath("$.userId").value(adminId.toString()))
                                .andExpect(jsonPath("$.tenantId").doesNotExist())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.sessionToken");

        assertThat(
                        jdbc.queryForMap(
                                "select tenant_id, user_id, admin_id from identity.user_sessions"
                                        + " where token_hash = ?",
                                tokens.hash(session)))
                .containsEntry("tenant_id", null)
                .containsEntry("user_id", null)
                .containsEntry("admin_id", adminId);

        mockMvc.perform(bearer(get("/api/v1/admin/universities"), session))
                .andExpect(status().isOk());
        mockMvc.perform(bearer(get("/api/v1/wallet"), session))
                .andExpect(status().isForbidden());
        mockMvc.perform(bearer(get("/api/v1/coordinator/indicators"), session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A coordinator reaches the coordinator routes and not the students ones")
    void aCoordinatorReachesTheCoordinatorRoutes() throws Exception {
        Tenant university = aNewUniversity();
        UUID coordinator = aCoordinatorOf(university);
        String session = sessionOf(university.getCode(), coordinator, Duration.ofDays(7));

        mockMvc.perform(
                        bearer(put("/api/v1/coordinator/university-identity"), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"primaryColor\": \"#003A70\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(university.getCode()));
        mockMvc.perform(bearer(get("/api/v1/wallet"), session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This route is for STUDENT"));
        // Their own profile is for whoever is signed in into a university.
        mockMvc.perform(bearer(get("/api/v1/me"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(coordinator.toString()));
    }

    @Test
    @DisplayName("The public routes answer without an account")
    void thePublicRoutesAnswerWithoutAnAccount() throws Exception {
        mockMvc.perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"%s\"}".formatted(ANAS_EMAIL)))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
