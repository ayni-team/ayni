package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.identity.UniversityRegistrationSupport.ANA;
import static pe.ayni.identity.UniversityRegistrationSupport.BRUNO;
import static pe.ayni.identity.UniversityRegistrationSupport.domainOf;
import static pe.ayni.identity.UniversityRegistrationSupport.list;
import static pe.ayni.identity.UniversityRegistrationSupport.newCode;
import static pe.ayni.identity.UniversityRegistrationSupport.profile;
import static pe.ayni.identity.UniversityRegistrationSupport.register;
import static pe.ayni.identity.UniversityRegistrationSupport.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.application.ImportAcademicRecordUseCase;
import pe.ayni.identity.domain.model.OnboardingStep;
import pe.ayni.identity.domain.model.User;
import pe.ayni.identity.domain.model.UserStatus;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.domain.Credits;
import pe.ayni.shared.tenancy.TenantContext;

/**
 * TS10 over HTTP against a real PostgreSQL: one test per scenario of {@code
 * features/TS10-register-university.feature}, with the same display name.
 *
 * <p>Scenario 1 runs in {@link OnboardingByConfigurationAcceptanceTest}, with real session tokens
 * and an academic directory of its own.
 *
 * <p>The other three check what identity owns. The students of the new university are stored
 * directly, because activating one is that scenario 1. Other modules read the university through
 * {@link IdentityApi}, so the policies are asserted there.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IdentityTestConfig.class)
class RegisterUniversityAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final UUID ADMINISTRATOR = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000002");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private ImportAcademicRecordUseCase importAcademicRecord;
    @Autowired private IdentityApi identity;

    @BeforeEach
    void theDemoUniversityAndItsStudents() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        new DemoIdentityData(tenants, users, importAcademicRecord, clock).run(null);
    }

    private String registerUniversity(String grade, int credits, int validityDays)
            throws Exception {
        String code = newCode();
        register(
                        mockMvc,
                        ADMINISTRATOR,
                        registration(code, domainOf(code), grade, credits, validityDays))
                .andExpect(status().isCreated());
        return code;
    }

    /** A student of the university, stored directly: activating one is not possible yet. */
    private UUID storeStudent(String tenant, String domain) {
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        users.save(
                new User(
                        id,
                        tenant,
                        UserRole.STUDENT,
                        "s0001@" + domain,
                        "S0001",
                        "Nora Quispe",
                        "Law",
                        "2026-2",
                        null,
                        null,
                        UserStatus.ACTIVE,
                        OnboardingStep.PROFILE,
                        now,
                        now,
                        now));
        return id;
    }

    private JsonNode entryOf(String code) throws Exception {
        String body =
                list(mockMvc, ADMINISTRATOR)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        for (JsonNode university : JSON.readTree(body)) {
            if (university.get("code").asText().equals(code)) {
                return university;
            }
        }
        throw new AssertionError("The listing has no university " + code);
    }

    private record Configuration(
            BigDecimal grade, Optional<CreditPolicyView> policy, boolean active, int policies) {}

    private Configuration configurationOf(String code) {
        TenantView tenant = identity.requireTenant(code);
        return new Configuration(
                tenant.minimumTeachingGrade(),
                identity.currentPolicy(code, PolicyKind.BASELINE),
                tenant.active(),
                jdbc.queryForObject(
                        "select count(*) from identity.credit_policies where tenant_id = ?",
                        Integer.class,
                        code));
    }

    @Test
    @DisplayName("Isolation from the first day")
    void isolationFromTheFirstDay() throws Exception {

        String code = registerUniversity("14.00", 5, 30);
        UUID nora = storeStudent(code, domainOf(code));

        // Each one is found in their own university...
        profile(mockMvc, "UPC", ANA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("UPC"));
        profile(mockMvc, code, nora)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(code))
                .andExpect(jsonPath("$.fullName").value("Nora Quispe"));

        // ...and nobody in the other one, in both directions.
        profile(mockMvc, code, ANA).andExpect(status().isNotFound());
        profile(mockMvc, code, BRUNO).andExpect(status().isNotFound());
        profile(mockMvc, "UPC", nora).andExpect(status().isNotFound());

        // The way the other modules read people: through the published interface.
        TenantContext.runAs(
                code,
                () -> {
                    assertThatThrownBy(() -> identity.requireUser(ANA))
                            .isInstanceOf(NoSuchElementException.class);
                    assertThat(identity.isActive(ANA)).isFalse();
                    assertThat(identity.approvedCourses(BRUNO)).isEmpty();
                    assertThat(identity.requireUser(nora).tenantId()).isEqualTo(code);
                });
        TenantContext.runAs(
                "UPC",
                () -> {
                    assertThatThrownBy(() -> identity.requireUser(nora))
                            .isInstanceOf(NoSuchElementException.class);
                    assertThat(identity.isActive(nora)).isFalse();
                    // Bruno's courses are there for UPC, which is what makes the empty list above
                    // mean something.
                    assertThat(identity.approvedCourses(BRUNO)).isNotEmpty();
                });

        // The directory counts each university's people apart.
        assertThat(entryOf(code).get("studentCount").asInt()).isEqualTo(1);
        assertThat(entryOf("UPC").get("studentCount").asInt())
                .isEqualTo(
                        jdbc.queryForObject(
                                "select count(*) from identity.users"
                                        + " where tenant_id = 'UPC' and role = 'STUDENT'",
                                Integer.class));

        // An email of each one leads to its own university.
        assertThat(identity.findTenantByEmailDomain("u202400001@upc.edu.pe").orElseThrow().code())
                .isEqualTo("UPC");
        assertThat(identity.findTenantByEmailDomain("s0001@" + domainOf(code)).orElseThrow().code())
                .isEqualTo(code);
    }

    @Test
    @DisplayName("Each university has its own policies")
    void eachUniversityHasItsOwnPolicies() throws Exception {

        String other = registerUniversity("12.00", 4, 20);
        Configuration upcBefore = configurationOf("UPC");
        Configuration otherBefore = configurationOf(other);

        String code = registerUniversity("16.50", 9, 45);

        Configuration registered = configurationOf(code);
        assertThat(registered.grade()).isEqualByComparingTo("16.50");
        CreditPolicyView policy = registered.policy().orElseThrow();
        assertThat(policy.kind()).isEqualTo(PolicyKind.BASELINE);
        assertThat(policy.amount()).isEqualTo(Credits.of(9));
        assertThat(policy.validityDays()).isEqualTo(45);
        assertThat(registered.policies()).isEqualTo(1);
        assertThat(entryOf(code).get("minimumTeachingGrade").decimalValue())
                .isEqualByComparingTo("16.50");

        // The others are what they were, and still have their own.
        assertThat(configurationOf("UPC")).isEqualTo(upcBefore);
        assertThat(configurationOf(other)).isEqualTo(otherBefore);
        assertThat(otherBefore.grade()).isEqualByComparingTo("12.00");
        assertThat(otherBefore.policy().orElseThrow().amount()).isEqualTo(Credits.of(4));
        assertThat(otherBefore.policy().orElseThrow().validityDays()).isEqualTo(20);
        assertThat(entryOf("UPC").get("minimumTeachingGrade").decimalValue())
                .isEqualByComparingTo("13.00");
    }

    @Test
    @DisplayName("No interruption of the service")
    void noInterruptionOfTheService() throws Exception {

        Configuration upcBefore = configurationOf("UPC");
        List<String> domainsBefore = tenants.findByCode("UPC").orElseThrow().getEmailDomains();

        // Students of UPC keep asking for their profile, with no pause, while the registrations
        // go through. The answer of every request is kept.
        List<Integer> answers = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean registering = new AtomicBoolean(true);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> students =
                    pool.submit(
                            () -> {
                                UUID[] people = {ANA, BRUNO};
                                int turn = 0;
                                while (registering.get()) {
                                    try {
                                        answers.add(
                                                profile(mockMvc, "UPC", people[turn++ % 2])
                                                        .andReturn()
                                                        .getResponse()
                                                        .getStatus());
                                    } catch (Exception failure) {
                                        answers.add(500);
                                    }
                                }
                            });

            for (int i = 0; i < 5; i++) {
                int before = answers.size();
                registerUniversity("13.00", 5, 30);
                // The students were being served while that registration completed.
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (answers.size() == before && System.nanoTime() < deadline) {
                    Thread.sleep(2);
                }
                assertThat(answers.size()).isGreaterThan(before);
            }
            registering.set(false);
            students.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(answers).isNotEmpty().containsOnly(200);
        assertThat(configurationOf("UPC")).isEqualTo(upcBefore);
        assertThat(tenants.findByCode("UPC").orElseThrow().getEmailDomains())
                .isEqualTo(domainsBefore);
        assertThat(identity.activeTenantCodes()).contains("UPC");
        assertThat(identity.findTenantByEmailDomain("u202400001@upc.edu.pe").orElseThrow().code())
                .isEqualTo("UPC");
    }
}
