package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.identity.UniversityRegistrationSupport.domainOf;
import static pe.ayni.identity.UniversityRegistrationSupport.newCode;
import static pe.ayni.identity.UniversityRegistrationSupport.register;
import static pe.ayni.identity.UniversityRegistrationSupport.registration;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.application.GeneratedAccessToken;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.domain.model.AccessLink;
import pe.ayni.identity.domain.model.AccessPurpose;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.AccessLinkRepository;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.CoordinatorActivated;

/**
 * TS10-T2 over HTTP against a real PostgreSQL: one test per scenario of {@code
 * features/TS10-coordinator-invitations.feature}, with the same display name, the refusals the
 * scenarios do not name, and the races of the invitation.
 *
 * <p>The raw token is read from {@link AccessRequested}, the way notifications read it to send the
 * email: the database only has its hash.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(IdentityTestConfig.class)
class CoordinatorInvitationAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final UUID ADMINISTRATOR = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000005");

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private AccessTokenGenerator tokens;
    @Autowired private AccessLinkRepository accessLinks;
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

    private static String emailIn(String code) {
        return "c" + UUID.randomUUID().toString().substring(0, 8) + "@" + domainOf(code);
    }

    private ResultActions invite(String code, String email) throws Exception {
        return mockMvc.perform(
                post("/api/v1/admin/universities/%s/coordinators".formatted(code))
                        .header("X-User-Id", ADMINISTRATOR.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\": \"%s\", \"fullName\": \"Maria Coordinator\"}"
                                        .formatted(email)));
    }

    private ResultActions confirm(String token) throws Exception {
        return mockMvc.perform(
                post("/api/v1/access/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(token)));
    }

    /** The token the email of the invitation to that address would carry. */
    private String tokenSentTo(String email) {
        String link =
                events.stream(AccessRequested.class)
                        .filter(event -> event.email().equals(email))
                        .reduce((first, second) -> second)
                        .orElseThrow()
                        .accessLink();
        return UriComponentsBuilder.fromUriString(link).build().getQueryParams().getFirst("token");
    }

    private String statusOf(String code, String email) {
        return jdbc.queryForObject(
                "select status from identity.users where tenant_id = ? and email = ?",
                String.class,
                code,
                email);
    }

    private int sessionsOf(String code, String email) {
        return jdbc.queryForObject(
                "select count(*) from identity.user_sessions s join identity.users u"
                        + " on u.id = s.user_id where u.tenant_id = ? and u.email = ?",
                Integer.class,
                code,
                email);
    }

    private long activations(String code, String email) {
        UUID id =
                jdbc.queryForObject(
                        "select id from identity.users where tenant_id = ? and email = ?",
                        UUID.class,
                        code,
                        email);
        return events.stream(CoordinatorActivated.class)
                .filter(event -> event.userId().equals(id))
                .count();
    }

    @Test
    @DisplayName("An administrator invites a coordinator")
    void anAdministratorInvitesACoordinator() throws Exception {

        String code = registerUniversity();
        String email = emailIn(code);

        invite(code, email).andExpect(status().isCreated());

        // A pending coordinator account, without a student code or an onboarding.
        assertThat(
                        jdbc.queryForMap(
                                "select role, status, student_code, activated_at"
                                        + " from identity.users where tenant_id = ? and email = ?",
                                code,
                                email))
                .containsEntry("role", "COORDINATOR")
                .containsEntry("status", "PENDING")
                .containsEntry("student_code", null)
                .containsEntry("activated_at", null);

        // A single use link, sent to that email, kept only as its hash.
        AccessRequested sent =
                events.stream(AccessRequested.class)
                        .filter(event -> event.email().equals(email))
                        .findFirst()
                        .orElseThrow();
        assertThat(sent.tenantId()).isEqualTo(code);
        assertThat(sent.purpose()).isEqualTo("COORDINATOR_INVITE");
        assertThat(sent.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
        String token = tokenSentTo(email);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from identity.access_links where tenant_id = ?"
                                        + " and email = ? and purpose = 'COORDINATOR_INVITE'"
                                        + " and consumed_at is null and token_hash = ?",
                                Integer.class,
                                code,
                                email,
                                tokens.hash(token)))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from identity.access_links where token_hash = ?",
                                Integer.class,
                                token))
                .isZero();
    }

    @Test
    @DisplayName("The invited coordinator follows the link")
    void theInvitedCoordinatorFollowsTheLink() throws Exception {

        String code = registerUniversity();
        String email = emailIn(code);
        invite(code, email).andExpect(status().isCreated());
        String token = tokenSentTo(email);

        confirm(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(code))
                .andExpect(jsonPath("$.sessionToken").isNotEmpty());

        assertThat(statusOf(code, email)).isEqualTo("ACTIVE");
        assertThat(sessionsOf(code, email)).isEqualTo(1);
        assertThat(activations(code, email)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select activated_at is not null from identity.users"
                                        + " where tenant_id = ? and email = ?",
                                Boolean.class,
                                code,
                                email))
                .isTrue();
    }

    @Test
    @DisplayName("The same invitation cannot activate twice")
    void theSameInvitationCannotActivateTwice() throws Exception {

        String code = registerUniversity();
        String email = emailIn(code);
        invite(code, email).andExpect(status().isCreated());
        String token = tokenSentTo(email);
        confirm(token).andExpect(status().isOk());

        confirm(token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(sessionsOf(code, email)).isEqualTo(1);
        assertThat(activations(code, email)).isEqualTo(1);
        assertThat(statusOf(code, email)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("An email that already has a user cannot be invited again")
    void anEmailThatAlreadyHasAUserCannotBeInvitedAgain() throws Exception {

        String code = registerUniversity();
        String email = emailIn(code);
        invite(code, email).andExpect(status().isCreated());
        int links = linksTo(code, email);

        // Still pending, and then active: either way the address already has a user.
        invite(code, email).andExpect(status().isConflict());
        confirm(tokenSentTo(email)).andExpect(status().isOk());
        invite(code, email).andExpect(status().isConflict());

        assertThat(linksTo(code, email)).isEqualTo(links);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from identity.users where tenant_id = ?"
                                        + " and email = ?",
                                Integer.class,
                                code,
                                email))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("An invitation of a university that cannot take it is refused")
    void anInvitationOfAUniversityThatCannotTakeItIsRefused() throws Exception {

        String code = registerUniversity();

        invite("NOSUCHUNIVERSITY", "someone@nosuchuniversity.edu.pe")
                .andExpect(status().isNotFound());
        invite(code, "someone@gmail.com").andExpect(status().isBadRequest());

        jdbc.update("update identity.tenants set status = 'SUSPENDED' where code = ?", code);
        invite(code, emailIn(code)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("An invitation of an account that is not a coordinator is forbidden")
    void anInvitationOfAnAccountThatIsNotACoordinatorIsForbidden() throws Exception {

        String code = registerUniversity();
        String email = emailIn(code);
        Instant now = clock.instant();
        users.save(
                new User(
                        UUID.randomUUID(),
                        code,
                        UserRole.STUDENT,
                        email,
                        "S" + UUID.randomUUID().toString().substring(0, 8),
                        "Nora Quispe",
                        "Law",
                        "2026-2",
                        null,
                        null,
                        UserStatus.PENDING,
                        OnboardingStep.PROFILE,
                        null,
                        now,
                        now));
        GeneratedAccessToken token = tokens.generate();
        accessLinks.save(
                new AccessLink(
                        UUID.randomUUID(),
                        code,
                        email,
                        AccessPurpose.COORDINATOR_INVITE,
                        token.tokenHash(),
                        now.plus(Duration.ofMinutes(10)),
                        null,
                        now));

        confirm(token.rawToken()).andExpect(status().isForbidden());

        assertThat(statusOf(code, email)).isEqualTo("PENDING");
        assertThat(sessionsOf(code, email)).isZero();
    }

    @Test
    @DisplayName("Two confirmations of the same invitation at the same time activate once")
    void twoConfirmationsOfTheSameInvitationAtOnce() throws Exception {

        // One round can be won by luck: the first commit may land before the second one reads.
        // Several rounds make a missing guard show up every time.
        for (int round = 1; round <= 6; round++) {
            String code = registerUniversity();
            String email = emailIn(code);
            invite(code, email).andExpect(status().isCreated());
            String token = tokenSentTo(email);

            List<Integer> statuses =
                    race(() -> answerOf(confirm(token)), () -> answerOf(confirm(token)));

            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 409);
            assertThat(statusOf(code, email)).isEqualTo("ACTIVE");
            assertThat(sessionsOf(code, email)).as("sessions, round %d", round).isEqualTo(1);
            assertThat(activations(code, email))
                    .as("CoordinatorActivated, round %d", round)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Two invitations to the same email at the same time end with a 201 and a 409")
    void twoInvitationsToTheSameEmailAtOnce() throws Exception {

        for (int round = 1; round <= 6; round++) {
            String code = registerUniversity();
            String email = emailIn(code);

            List<Integer> statuses =
                    race(() -> answerOf(invite(code, email)), () -> answerOf(invite(code, email)));

            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(201, 409);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from identity.users where tenant_id = ?"
                                            + " and email = ?",
                                    Integer.class,
                                    code,
                                    email))
                    .isEqualTo(1);
            assertThat(linksTo(code, email)).isEqualTo(1);
        }
    }

    private int linksTo(String code, String email) {
        return jdbc.queryForObject(
                "select count(*) from identity.access_links where tenant_id = ? and email = ?",
                Integer.class,
                code,
                email);
    }

    private static int answerOf(ResultActions request) {
        try {
            return request.andReturn().getResponse().getStatus();
        } catch (Exception failure) {
            // What the server would have answered: MockMvc rethrows it.
            return 500;
        }
    }

    /** Both requests start behind a barrier, so they are in flight together. */
    private static List<Integer> race(Callable<Integer> first, Callable<Integer> second)
            throws Exception {
        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (Callable<Integer> request : List.of(first, second)) {
                answers.add(
                        pool.submit(
                                () -> {
                                    bothReady.await();
                                    try {
                                        return request.call();
                                    } catch (Exception failure) {
                                        return 500;
                                    }
                                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
