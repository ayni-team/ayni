package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.identity.UniversityRegistrationSupport.domainOf;
import static pe.ayni.identity.UniversityRegistrationSupport.newCode;
import static pe.ayni.identity.UniversityRegistrationSupport.register;
import static pe.ayni.identity.UniversityRegistrationSupport.registration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * US52 over HTTP against a real PostgreSQL: one test per scenario of {@code
 * features/US52-credit-policy.feature}, with the same display name, the refusals the scenarios do
 * not name, and two coordinators replacing the policy at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IdentityTestConfig.class)
class CreditPolicyAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final UUID ADMINISTRATOR = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000004");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private AutowireCapableBeanFactory beans;
    @Autowired private IdentityApi identity;

    @BeforeEach
    void theDemoUniversityAndItsStudents() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        beans.createBean(DemoIdentityData.class).run(null);
    }

    /** A university registered with a baseline of 5 credits for 30 days. */
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
                        OnboardingStep.PROFILE,
                        status == UserStatus.ACTIVE ? now : null,
                        now,
                        now));
        return id;
    }

    private ResultActions changePolicy(String tenant, UUID user, int credits, int validityDays)
            throws Exception {
        return mockMvc.perform(
                put("/api/v1/coordinator/credit-policy")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", user.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"creditsAmount\": %d, \"validityDays\": %d}"
                                        .formatted(credits, validityDays)));
    }

    private int current(String tenant) {
        return jdbc.queryForObject(
                "select count(*) from identity.credit_policies"
                        + " where tenant_id = ? and kind = 'BASELINE' and superseded_at is null",
                Integer.class,
                tenant);
    }

    private int total(String tenant) {
        return jdbc.queryForObject(
                "select count(*) from identity.credit_policies where tenant_id = ?",
                Integer.class,
                tenant);
    }

    private ResultActions readPolicy(String tenant, UUID user) throws Exception {
        return mockMvc.perform(
                get("/api/v1/coordinator/credit-policy")
                        .header("X-Tenant-Id", tenant)
                        .header("X-User-Id", user.toString()));
    }

    @Test
    @DisplayName("A coordinator reads the current baseline credit policy")
    void aCoordinatorReadsTheCurrentBaselineCreditPolicy() throws Exception {

        String code = registerUniversity();
        UUID coordinator = store(code, UserRole.COORDINATOR, UserStatus.ACTIVE);

        readPolicy(code, coordinator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("BASELINE"))
                .andExpect(jsonPath("$.creditsAmount").value(5))
                .andExpect(jsonPath("$.validityDays").value(30));

        changePolicy(code, coordinator, 8, 45).andExpect(status().isOk());

        readPolicy(code, coordinator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditsAmount").value(8))
                .andExpect(jsonPath("$.validityDays").value(45));
    }

    @Test
    @DisplayName("Only an active coordinator of the university reads its credit policy")
    void onlyAnActiveCoordinatorOfTheUniversityReadsItsCreditPolicy() throws Exception {

        String code = registerUniversity();
        UUID student = store(code, UserRole.STUDENT, UserStatus.ACTIVE);
        UUID restricted = store(code, UserRole.COORDINATOR, UserStatus.RESTRICTED);
        String other = registerUniversity();
        UUID otherCoordinator = store(other, UserRole.COORDINATOR, UserStatus.ACTIVE);

        readPolicy(code, student).andExpect(status().isForbidden());
        readPolicy(code, restricted).andExpect(status().isForbidden());
        // A coordinator of another university is nobody here.
        readPolicy(code, otherCoordinator).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A coordinator changes the baseline credit policy")
    void aCoordinatorChangesTheBaselineCreditPolicy() throws Exception {

        String code = registerUniversity();
        UUID coordinator = store(code, UserRole.COORDINATOR, UserStatus.ACTIVE);
        UUID previous =
                jdbc.queryForObject(
                        "select id from identity.credit_policies"
                                + " where tenant_id = ? and superseded_at is null",
                        UUID.class,
                        code);

        String created =
                changePolicy(code, coordinator, 8, 45)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.kind").value("BASELINE"))
                        .andExpect(jsonPath("$.creditsAmount").value(8))
                        .andExpect(jsonPath("$.validityDays").value(45))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // The previous one is closed and still there.
        Map<String, Object> old =
                jdbc.queryForMap(
                        "select credits_amount, validity_days, superseded_at"
                                + " from identity.credit_policies where id = ?",
                        previous);
        assertThat(old.get("superseded_at")).isNotNull();
        assertThat(old.get("credits_amount")).isEqualTo(5);
        assertThat(old.get("validity_days")).isEqualTo(30);

        // The new one is the only current one, and names the coordinator as its author.
        assertThat(total(code)).isEqualTo(2);
        assertThat(current(code)).isEqualTo(1);
        Map<String, Object> now =
                jdbc.queryForMap(
                        "select credits_amount, validity_days, created_by_user, created_by_admin"
                                + " from identity.credit_policies"
                                + " where tenant_id = ? and superseded_at is null",
                        code);
        assertThat(now.get("credits_amount")).isEqualTo(8);
        assertThat(now.get("validity_days")).isEqualTo(45);
        assertThat(now.get("created_by_user")).isEqualTo(coordinator);
        assertThat(now.get("created_by_admin")).isNull();
        assertThat(created).contains(now.get("credits_amount").toString());
    }

    @Test
    @DisplayName("Future student grants use the new policy")
    void futureStudentGrantsUseTheNewPolicy() throws Exception {

        String code = registerUniversity();
        UUID coordinator = store(code, UserRole.COORDINATOR, UserStatus.ACTIVE);
        String other = registerUniversity();
        changePolicy(code, coordinator, 8, 45).andExpect(status().isOk());

        CreditPolicyView current = identity.currentPolicy(code, PolicyKind.BASELINE).orElseThrow();
        assertThat(current.kind()).isEqualTo(PolicyKind.BASELINE);
        assertThat(current.amount()).isEqualTo(Credits.of(8));
        assertThat(current.validityDays()).isEqualTo(45);

        // The superseded one is never what is returned: there is exactly one current policy.
        assertThat(current(code)).isEqualTo(1);
        TenantContext.runAs(
                code,
                () ->
                        assertThat(identity.currentPolicy(code, PolicyKind.BASELINE).orElseThrow())
                                .isEqualTo(current));

        // Another university keeps its own.
        CreditPolicyView untouched = identity.currentPolicy(other, PolicyKind.BASELINE).orElseThrow();
        assertThat(untouched.amount()).isEqualTo(Credits.of(5));
        assertThat(untouched.validityDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("A student cannot change university credit policy")
    void aStudentCannotChangeUniversityCreditPolicy() throws Exception {

        String code = registerUniversity();
        UUID student = store(code, UserRole.STUDENT, UserStatus.ACTIVE);

        changePolicy(code, student, 8, 45)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertThat(total(code)).isEqualTo(1);
        assertThat(identity.currentPolicy(code, PolicyKind.BASELINE).orElseThrow().amount())
                .isEqualTo(Credits.of(5));

        // Neither does a coordinator who is restricted, nor somebody who is not in the university.
        UUID restricted = store(code, UserRole.COORDINATOR, UserStatus.RESTRICTED);
        changePolicy(code, restricted, 8, 45).andExpect(status().isForbidden());
        changePolicy(code, UUID.randomUUID(), 8, 45).andExpect(status().isForbidden());
        assertThat(total(code)).isEqualTo(1);
    }

    @Test
    @DisplayName("Invalid credit policy values are rejected")
    void invalidCreditPolicyValuesAreRejected() throws Exception {

        String code = registerUniversity();
        UUID coordinator = store(code, UserRole.COORDINATOR, UserStatus.ACTIVE);

        changePolicy(code, coordinator, 0, 45).andExpect(status().isBadRequest());
        changePolicy(code, coordinator, 8, 0).andExpect(status().isBadRequest());
        changePolicy(code, coordinator, -1, -1).andExpect(status().isBadRequest());

        assertThat(total(code)).isEqualTo(1);
        assertThat(current(code)).isEqualTo(1);
        assertThat(identity.currentPolicy(code, PolicyKind.BASELINE).orElseThrow().amount())
                .isEqualTo(Credits.of(5));
    }

    @Test
    @DisplayName("Two coordinators replacing the policy at the same time leave one current baseline")
    void twoCoordinatorsReplacingThePolicyAtOnce() throws Exception {

        String code = registerUniversity();
        UUID first = store(code, UserRole.COORDINATOR, UserStatus.ACTIVE);
        UUID second = store(code, UserRole.COORDINATOR, UserStatus.ACTIVE);

        // One round can be won by luck: the first commit may land before the second one reads.
        // Several rounds make a missing lock show up every time.
        for (int round = 1; round <= 6; round++) {
            List<Integer> statuses = race(code, first, second, 10 + round, 20 + round);

            assertThat(statuses).as("round %d", round).containsOnly(200);
            assertThat(current(code)).as("current policies, round %d", round).isEqualTo(1);
            assertThat(total(code)).isEqualTo(1 + 2 * round);
            assertThat(
                            jdbc.queryForObject(
                                    "select count(*) from identity.credit_policies"
                                            + " where tenant_id = ? and superseded_at is not null",
                                    Integer.class,
                                    code))
                    .isEqualTo(2 * round);
            // And the published interface can still read it.
            assertThat(identity.currentPolicy(code, PolicyKind.BASELINE)).isPresent();
        }
    }

    private List<Integer> race(String code, UUID first, UUID second, int a, int b)
            throws Exception {
        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            answers.add(pool.submit(() -> attempt(bothReady, code, first, a)));
            answers.add(pool.submit(() -> attempt(bothReady, code, second, b)));
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int attempt(CyclicBarrier bothReady, String code, UUID user, int credits)
            throws Exception {
        bothReady.await();
        try {
            return changePolicy(code, user, credits, 30).andReturn().getResponse().getStatus();
        } catch (Exception failure) {
            // What the server would have answered: MockMvc rethrows it.
            return 500;
        }
    }
}
