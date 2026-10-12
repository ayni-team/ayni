package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.StudentActivated;
/**
 * US38 over HTTP against a real PostgreSQL: asking for a link, opening it, and every way opening it
 * must fail. One test per confirmation scenario of {@code features/US38-request-access.feature}.
 *
 * <p>The people are the demo ones, created by {@link DemoIdentityData} itself, so the test signs in
 * as the same Ana a developer signs in as. The raw token is read from {@link AccessRequested}, the
 * way notifications will read it to send the email: the database only has its hash.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(IdentityTestConfig.class)
class ConfirmAccessAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    /** Ana, of {@code DemoIdentityData}: an active student of UPC. */
    private static final UUID ANA = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final String ANAS_EMAIL = "u202400001@upc.edu.pe";

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private AccessTokenGenerator tokens;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private ImportAcademicRecordUseCase importAcademicRecord;

    @BeforeEach
    void theDemoUniversityAndItsStudents() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        // Idempotent: the first test creates them, the others find them there.
        new DemoIdentityData(tenants, users, importAcademicRecord, clock).run(null);
    }

    /** Asks for a link and returns what the email would carry. */
    private String requestLink(String email) throws Exception {
        mockMvc.perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());

        return events.stream(AccessRequested.class)
                .filter(event -> event.email().equals(email))
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

    private int sessionsOf(UUID userId) {
        return jdbc.queryForObject(
                "select count(*) from identity.user_sessions where user_id = ?",
                Integer.class,
                userId);
    }

    @Test
    @DisplayName("Signing in with a valid link")
    void signingInWithAValidLink() throws Exception {

        String link = requestLink(ANAS_EMAIL);
        UriComponents url = UriComponentsBuilder.fromUriString(link).build();
        // The link carries the token and nothing that names the university.
        assertThat(url.getQueryParams().keySet()).containsExactly("token");

        MvcResult answer =
                confirm(tokenOf(link))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tenantId").value("UPC"))
                        .andExpect(jsonPath("$.userId").value(ANA.toString()))
                        .andExpect(
                                jsonPath("$.expiresAt")
                                        .value(clock.instant().plus(Duration.ofDays(7)).toString()))
                        .andReturn();
        String sessionToken =
                JsonPath.read(answer.getResponse().getContentAsString(), "$.sessionToken");

        // The session exists, for Ana in UPC, and the database holds its hash, never the token.
        assertThat(
                        jdbc.queryForObject(
                                """
                                select count(*) from identity.user_sessions
                                where token_hash = ? and user_id = ? and tenant_id = 'UPC'
                                  and revoked_at is null
                                """,
                                Integer.class,
                                tokens.hash(sessionToken),
                                ANA))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from identity.user_sessions where token_hash = ?",
                                Integer.class,
                                sessionToken))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select consumed_at is not null from identity.access_links"
                                        + " where token_hash = ?",
                                Boolean.class,
                                tokens.hash(tokenOf(link))))
                .isTrue();
    }

    @Test
    @DisplayName("A link that was already used")
    void aLinkThatWasAlreadyUsed() throws Exception {

        String token = tokenOf(requestLink(ANAS_EMAIL));
        confirm(token).andExpect(status().isOk());
        int sessions = sessionsOf(ANA);

        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(
                        jsonPath("$.message")
                                .value("This access link has expired or was already used."
                                        + " Ask for a new one"))
                .andExpect(jsonPath("$.path").value("/api/v1/access/confirm"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(sessionsOf(ANA)).isEqualTo(sessions);
    }

    @Test
    @DisplayName("An expired link")
    void anExpiredLink() throws Exception {

        String token = tokenOf(requestLink(ANAS_EMAIL));
        int sessions = sessionsOf(ANA);

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        confirm(token)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("This access link has expired or was already used."
                                        + " Ask for a new one"));

        assertThat(sessionsOf(ANA)).isEqualTo(sessions);
    }

    @Test
    @DisplayName("A token nobody issued")
    void aTokenNobodyIssued() throws Exception {

        confirm("an-invented-token-that-was-never-sent")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value("This access link is not valid. Ask for a new one"));
    }

    @Test
    @DisplayName("Two confirmations of the same link at the same time open one session")
    void twoConfirmationsAtOnceOpenOneSession() throws Exception {

        String token = tokenOf(requestLink(ANAS_EMAIL));
        int sessions = sessionsOf(ANA);

        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                answers.add(
                        pool.submit(
                                () -> {
                                    bothReady.await();
                                    return confirm(token).andReturn().getResponse().getStatus();
                                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get());
            }

            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        } finally {
            pool.shutdownNow();
        }

        assertThat(sessionsOf(ANA)).isEqualTo(sessions + 1);
    }

    @Test
    @DisplayName("First access of a new student creates the account from the academic system")
    void firstAccessActivatesNewStudent() throws Exception {

        String email =
                "u202500003@upc.edu.pe";

        String link =
                requestLink(email);

        assertThat(
                events.stream(AccessRequested.class)
                        .filter(
                                event ->
                                        event.email()
                                                .equals(email))
                        .map(
                                AccessRequested::purpose))
                .containsOnly(
                        "ACTIVATION");

        MvcResult answer =
                confirm(tokenOf(link))
                        .andExpect(
                                status().isOk())
                        .andExpect(
                                jsonPath("$.tenantId")
                                        .value("UPC"))
                        .andReturn();

        String userIdText =
                JsonPath.read(
                        answer.getResponse()
                                .getContentAsString(),
                        "$.userId");

        UUID activatedUserId =
                UUID.fromString(
                        userIdText);

        assertThat(
                jdbc.queryForObject(
                        """
                        select full_name
                        from identity.users
                        where id = ?
                          and tenant_id = 'UPC'
                        """,
                        String.class,
                        activatedUserId))
                .isEqualTo(
                        "Carla Mendoza");

        assertThat(
                jdbc.queryForObject(
                        """
                        select student_code
                        from identity.users
                        where id = ?
                        """,
                        String.class,
                        activatedUserId))
                .isEqualTo(
                        "U202500003");

        assertThat(
                jdbc.queryForObject(
                        """
                        select career
                        from identity.users
                        where id = ?
                        """,
                        String.class,
                        activatedUserId))
                .isEqualTo(
                        "Software Engineering");

        assertThat(
                jdbc.queryForObject(
                        """
                        select current_term
                        from identity.users
                        where id = ?
                        """,
                        String.class,
                        activatedUserId))
                .isEqualTo(
                        "2026-2");

        assertThat(
                jdbc.queryForObject(
                        """
                        select status
                        from identity.users
                        where id = ?
                        """,
                        String.class,
                        activatedUserId))
                .isEqualTo(
                        "ACTIVE");

        assertThat(
                jdbc.queryForObject(
                        """
                        select count(*)
                        from identity.academic_records
                        where tenant_id = 'UPC'
                          and user_id = ?
                        """,
                        Integer.class,
                        activatedUserId))
                .isEqualTo(3);

        assertThat(
                sessionsOf(
                        activatedUserId))
                .isEqualTo(1);

        assertThat(
                events.stream(
                                StudentActivated.class)
                        .filter(
                                event ->
                                        event.userId()
                                                .equals(
                                                        activatedUserId))
                        .toList())
                .hasSize(1);

        StudentActivated activated =
                events.stream(
                                StudentActivated.class)
                        .filter(
                                event ->
                                        event.userId()
                                                .equals(
                                                        activatedUserId))
                        .findFirst()
                        .orElseThrow();

        assertThat(
                activated.tenantId())
                .isEqualTo("UPC");

        assertThat(
                activated.email())
                .isEqualTo(email);

        assertThat(
                activated.studentCode())
                .isEqualTo(
                        "U202500003");
    }
}
