package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.ayni.identity.UniversityRegistrationSupport.domainOf;
import static pe.ayni.identity.UniversityRegistrationSupport.list;
import static pe.ayni.identity.UniversityRegistrationSupport.newCode;
import static pe.ayni.identity.UniversityRegistrationSupport.register;
import static pe.ayni.identity.UniversityRegistrationSupport.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.identity.infrastructure.DemoIdentityData;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.shared.events.AccessRequested;
import pe.ayni.shared.events.UniversityRegistered;

/**
 * What registering and listing universities already did, proven over HTTP against a real
 * PostgreSQL, and the two requests that race for the same code or the same domain.
 *
 * <p>They are the contract of the two endpoints and not scenarios of the story, which are in
 * {@link RegisterUniversityAcceptanceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(IdentityTestConfig.class)
class UniversityAdministrationAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final UUID ADMINISTRATOR = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationEvents events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private AutowireCapableBeanFactory beans;

    @BeforeEach
    void theDemoUniversityAndItsStudents() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        beans.createBean(DemoIdentityData.class).run(null);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    @Test
    @DisplayName("Registering a university creates its baseline policy and announces it")
    void registeringCreatesTheBaselinePolicyAndAnnouncesIt() throws Exception {

        String code = newCode();

        // The code and the domain come in with any case and are kept in one.
        register(
                        mockMvc,
                        ADMINISTRATOR,
                        registration(
                                code.toLowerCase(),
                                domainOf(code).toUpperCase(),
                                "15.50",
                                7,
                                45))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("Universidad " + code.toLowerCase()))
                .andExpect(jsonPath("$.emailDomains[0]").value(domainOf(code)))
                .andExpect(jsonPath("$.minimumTeachingGrade").value(15.5))
                .andExpect(jsonPath("$.timezone").value("America/Lima"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.studentCount").value(0))
                .andExpect(jsonPath("$.coordinatorCount").value(0));

        assertThat(
                        count(
                                "select count(*) from identity.tenants"
                                        + " where code = ? and status = 'ACTIVE'",
                                code))
                .isEqualTo(1);

        // The initial policy: the baseline the administrator asked for, authored by them.
        assertThat(
                        count(
                                "select count(*) from identity.credit_policies where tenant_id = ?",
                                code))
                .isEqualTo(1);
        assertThat(
                        count(
                                """
                                select count(*) from identity.credit_policies
                                where tenant_id = ? and kind = 'BASELINE'
                                  and credits_amount = 7 and validity_days = 45
                                  and superseded_at is null
                                  and created_by_admin = ? and created_by_user is null
                                  and valid_from = ?
                                """,
                                code,
                                ADMINISTRATOR,
                                LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Lima"))))
                .isEqualTo(1);

        List<UniversityRegistered> announced =
                events.stream(UniversityRegistered.class)
                        .filter(event -> event.tenantId().equals(code))
                        .toList();
        assertThat(announced).hasSize(1);
        assertThat(announced.get(0).name()).isEqualTo("Universidad " + code.toLowerCase());
        assertThat(announced.get(0).occurredOn()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("The email domain of a registered university leads to it")
    void theEmailDomainOfARegisteredUniversityLeadsToIt() throws Exception {

        String code = newCode();
        register(mockMvc, ADMINISTRATOR, registration(code, domainOf(code), "13.00", 5, 30))
                .andExpect(status().isCreated());
        String email = "u0001@" + domainOf(code);

        mockMvc.perform(
                        post("/api/v1/access/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());

        assertThat(
                        events.stream(AccessRequested.class)
                                .filter(event -> event.email().equals(email))
                                .map(AccessRequested::tenantId))
                .containsExactly(code);
    }

    @Test
    @DisplayName("A refused registration leaves nothing behind")
    void aRefusedRegistrationLeavesNothingBehind() throws Exception {

        String code = newCode();
        register(mockMvc, ADMINISTRATOR, registration(code, domainOf(code), "13.00", 5, 30))
                .andExpect(status().isCreated());

        // The same code, in another case.
        register(
                        mockMvc,
                        ADMINISTRATOR,
                        registration(
                                code.toLowerCase(), domainOf(newCode()), "10.00", 99, 99))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("A university with this code already exists"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/universities"))
                .andExpect(jsonPath("$.timestamp").exists());

        // The same domain, in another case, under another code.
        String other = newCode();
        register(
                        mockMvc,
                        ADMINISTRATOR,
                        registration(other, domainOf(code).toUpperCase(), "10.00", 99, 99))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value("An institutional email domain is already registered"));

        // A domain UPC already has.
        register(mockMvc, ADMINISTRATOR, registration(other, "upc.edu.pe", "10.00", 99, 99))
                .andExpect(status().isConflict());

        assertThat(count("select count(*) from identity.tenants where code = ?", other)).isZero();
        assertThat(
                        count(
                                "select count(*) from identity.credit_policies where tenant_id = ?",
                                other))
                .isZero();
        // What was there is as it was, and only the first registration was announced.
        assertThat(
                        count(
                                "select count(*) from identity.credit_policies"
                                        + " where tenant_id = ? and credits_amount = 5",
                                code))
                .isEqualTo(1);
        assertThat(
                        events.stream(UniversityRegistered.class)
                                .filter(
                                        event ->
                                                event.tenantId().equals(code)
                                                        || event.tenantId().equals(other)))
                .hasSize(1);
    }

    @Test
    @DisplayName("A value that is wrong is a bad request, not a conflict")
    void aValueThatIsWrongIsABadRequest() throws Exception {

        String code = newCode();

        register(
                        mockMvc,
                        ADMINISTRATOR,
                        registration(code, domainOf(code), "13.00", 5, 30)
                                .replace("America/Lima", "Not/AZone"))
                .andExpect(status().isBadRequest());

        assertThat(count("select count(*) from identity.tenants where code = ?", code)).isZero();
    }

    @Test
    @DisplayName("A registration without its minimum teaching grade is a bad request")
    void aRegistrationWithoutAGradeIsABadRequest() throws Exception {

        String code = newCode();

        register(
                        mockMvc,
                        ADMINISTRATOR,
                        registration(code, domainOf(code), "13.00", 5, 30)
                                .replace("\"minimumTeachingGrade\": 13.00,", ""))
                .andExpect(status().isBadRequest());

        assertThat(count("select count(*) from identity.tenants where code = ?", code)).isZero();
    }

    @Test
    @DisplayName("The listing returns configuration and counts, never names, emails or courses")
    void theListingReturnsConfigurationAndCountsOnly() throws Exception {

        String body = list(mockMvc, ADMINISTRATOR).andExpect(status().isOk()).andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode universities = JSON.readTree(body);
        JsonNode upc = null;
        for (JsonNode university : universities) {
            if (university.get("code").asText().equals("UPC")) {
                upc = university;
            }
            // Whatever the university, the answer has the same fields and no others.
            Set<String> fields = new TreeSet<>();
            university.fieldNames().forEachRemaining(fields::add);
            assertThat(fields)
                    .containsExactlyInAnyOrder(
                            "code",
                            "name",
                            "logoUrl",
                            "primaryColor",
                            "secondaryColor",
                            "emailDomains",
                            "minimumTeachingGrade",
                            "timezone",
                            "active",
                            "studentCount",
                            "coordinatorCount");
        }
        assertThat(upc).isNotNull();

        // The counts are the people the table holds, and the people themselves are not in it.
        assertThat(upc.get("studentCount").asInt())
                .isEqualTo(
                        count(
                                "select count(*) from identity.users"
                                        + " where tenant_id = 'UPC' and role = 'STUDENT'"));
        assertThat(upc.get("studentCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(upc.get("coordinatorCount").asInt())
                .isEqualTo(
                        count(
                                "select count(*) from identity.users"
                                        + " where tenant_id = 'UPC' and role = 'COORDINATOR'"));

        List<String> personal = new ArrayList<>();
        personal.addAll(jdbc.queryForList("select full_name from identity.users", String.class));
        personal.addAll(jdbc.queryForList("select email from identity.users", String.class));
        personal.addAll(
                jdbc.queryForList(
                        "select student_code from identity.users where student_code is not null",
                        String.class));
        personal.addAll(
                jdbc.queryForList(
                        "select distinct course_name from identity.academic_records", String.class));
        assertThat(personal).contains("Ana Torres", "Bruno Salas", "Diseño de Base de Datos");
        assertThat(personal).allSatisfy(value -> assertThat(body).doesNotContain(value));

        // A course code is short, and the codes of the curricula (AD99, CC126) could appear by
        // chance inside the random code of a test's university: none is a value of the answer.
        List<String> courseCodes =
                jdbc.queryForList(
                        "select distinct course_code from identity.academic_records", String.class);
        assertThat(courseCodes).contains("SI400");
        List<String> values = new ArrayList<>();
        universities.forEach(
                university ->
                        university.forEach(
                                value -> {
                                    if (value.isArray()) {
                                        value.forEach(each -> values.add(each.asText()));
                                    } else {
                                        values.add(value.asText());
                                    }
                                }));
        assertThat(values).doesNotContainAnyElementsOf(courseCodes);
    }

    @Test
    @DisplayName("Two registrations of the same code at the same time end with a 201 and a 409")
    void twoRegistrationsOfTheSameCodeAtOnce() throws Exception {

        // One round can be won by luck: the first commit may land before the second one looks.
        // Several rounds make a missing lock show up every time.
        for (int round = 0; round < 6; round++) {
            String code = newCode();

            List<Integer> statuses =
                    raceOf(
                            registration(code, domainOf(newCode()), "13.00", 5, 30),
                            registration(code, domainOf(newCode()), "13.00", 5, 30));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(count("select count(*) from identity.tenants where code = ?", code))
                    .isEqualTo(1);
            assertThat(
                            count(
                                    "select count(*) from identity.credit_policies"
                                            + " where tenant_id = ?",
                                    code))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Two registrations of the same domain at the same time end with a 201 and a 409")
    void twoRegistrationsOfTheSameDomainAtOnce() throws Exception {

        for (int round = 0; round < 6; round++) {
            String domain = domainOf(newCode());
            String first = newCode();
            String second = newCode();

            List<Integer> statuses =
                    raceOf(
                            registration(first, domain, "13.00", 5, 30),
                            registration(second, domain, "13.00", 5, 30));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            // The table does not forbid it, so the only proof is that one university holds it.
            assertThat(
                            count(
                                    "select count(*) from identity.tenants where ? = any(email_domains)",
                                    domain))
                    .isEqualTo(1);
            assertThat(
                            count(
                                    "select count(*) from identity.tenants where code in (?, ?)",
                                    first,
                                    second))
                    .isEqualTo(1);
        }
    }

    /** Sends both bodies as registrations, released together by a barrier. */
    private List<Integer> raceOf(String first, String second) throws Exception {
        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (String body : List.of(first, second)) {
                answers.add(
                        pool.submit(
                                () -> {
                                    bothReady.await();
                                    try {
                                        return register(mockMvc, ADMINISTRATOR, body)
                                                .andReturn()
                                                .getResponse()
                                                .getStatus();
                                    } catch (Exception failure) {
                                        // What the server would have answered: MockMvc rethrows it.
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
