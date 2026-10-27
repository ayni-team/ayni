package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * Reading the university and editing its email domains (US50) over HTTP against a real PostgreSQL
 * with real session tokens (TS03). One test per scenario of {@code
 * features/US50-university-identity.feature} that names this class, with the same display name.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class UniversityEmailDomainsAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private UserSessionRepository userSessions;
    @Autowired private PlatformAdminRepository admins;
    @Autowired private AccessTokenGenerator tokens;

    private SignedInPeople people;
    private String code;
    private Person coordinator;
    private Person student;
    private String administrator;

    /** A university with its identity, its coordinator and a student, and a platform administrator. */
    @BeforeEach
    void aUniversityItsPeopleAndAnAdministrator() {
        people = new SignedInPeople(tenants, users, userSessions, tokens);
        code = people.aUniversity("D");
        Tenant tenant = tenants.findByCode(code).orElseThrow();
        tenant.updateIdentity("https://cdn.ayni.pe/" + code + ".png", "#003A70", "#F5F5F5", NOW);
        tenants.save(tenant);
        coordinator = people.aCoordinator(code);
        student = people.aStudent(code);
        administrator = administratorToken();
    }

    private String administratorToken() {
        UUID adminId = UUID.randomUUID();
        admins.save(
                new PlatformAdmin(
                        adminId, "admin-" + adminId + "@ayni.test", "Admin", PlatformAdminStatus.ACTIVE, NOW));
        String token = tokens.generate().rawToken();
        userSessions.save(
                UserSession.forPlatformAdmin(
                        UUID.randomUUID(), adminId, tokens.hash(token), NOW.plus(Duration.ofDays(30)), NOW));
        return token;
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private String domainOf(String university) {
        return university.toLowerCase() + ".edu.pe";
    }

    private List<String> domainsOf(String university) {
        return List.of(
                jdbc.queryForObject(
                                "select array_to_string(email_domains, ',') from identity.tenants where code = ?",
                                String.class,
                                university)
                        .split(","));
    }

    private ResultActions replaceDomains(String token, String university, String body) throws Exception {
        return mockMvc.perform(
                bearer(put("/api/v1/admin/universities/" + university + "/email-domains"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static String domains(String... values) {
        StringBuilder list = new StringBuilder("{\"emailDomains\": [");
        for (int i = 0; i < values.length; i++) {
            list.append(i == 0 ? "" : ", ").append('"').append(values[i]).append('"');
        }
        return list.append("]}").toString();
    }

    @Test
    @DisplayName("A student and a coordinator read their university")
    void aStudentAndACoordinatorReadTheirUniversity() throws Exception {

        String asStudent =
                mockMvc.perform(bearer(get("/api/v1/university"), student.token()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(code))
                        .andExpect(jsonPath("$.name").value("University " + code))
                        .andExpect(jsonPath("$.logoUrl").value("https://cdn.ayni.pe/" + code + ".png"))
                        .andExpect(jsonPath("$.primaryColor").value("#003A70"))
                        .andExpect(jsonPath("$.secondaryColor").value("#F5F5F5"))
                        .andExpect(jsonPath("$.emailDomains[0]").value(domainOf(code)))
                        .andExpect(jsonPath("$.timezone").value("America/Lima"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String asCoordinator =
                mockMvc.perform(bearer(get("/api/v1/university"), coordinator.token()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(asCoordinator).isEqualTo(asStudent);

        // Each one reads the university of their session, whatever the request says.
        String other = people.aUniversity("D");
        Person ofOther = people.aStudent(other);
        mockMvc.perform(bearer(get("/api/v1/university").param("code", code), ofOther.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(other));

        mockMvc.perform(get("/api/v1/university")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A platform administrator has no university to read")
    void aPlatformAdministratorHasNoUniversityToRead() throws Exception {

        mockMvc.perform(bearer(get("/api/v1/university"), administrator))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("The administrator replaces the email domains of a university")
    void theAdministratorReplacesTheEmailDomainsOfAUniversity() throws Exception {

        String added = code.toLowerCase() + "-new.edu.pe";
        String second = code.toLowerCase() + "-two.edu.pe";

        replaceDomains(administrator, code, domains(added.toUpperCase(), second, added))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.emailDomains.length()").value(2))
                .andExpect(jsonPath("$.emailDomains[0]").value(added))
                .andExpect(jsonPath("$.emailDomains[1]").value(second));

        assertThat(domainsOf(code)).containsExactly(added, second);

        // An email of a new domain now finds the university, and the one that was left out no longer.
        mockMvc.perform(get("/api/v1/access/university").param("email", "someone@" + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
        mockMvc.perform(get("/api/v1/access/university").param("email", "someone@" + domainOf(code)))
                .andExpect(status().isNotFound());

        // The coordinator and the student of the university read the new list.
        mockMvc.perform(bearer(get("/api/v1/university"), student.token()))
                .andExpect(jsonPath("$.emailDomains[1]").value(second));
    }

    @Test
    @DisplayName("The list of domains needs at least one domain")
    void theListOfDomainsNeedsAtLeastOneDomain() throws Exception {

        List<String> before = domainsOf(code);

        for (String body :
                List.of(
                        "{\"emailDomains\": []}",
                        "{}",
                        "{\"emailDomains\": null}",
                        domains(""),
                        domains("   "),
                        domains("valid.edu.pe", "someone@valid.edu.pe"),
                        domains("not a domain"))) {
            replaceDomains(administrator, code, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").exists());
        }

        assertThat(domainsOf(code)).isEqualTo(before);

        replaceDomains(administrator, "NOBODY", domains("nobody.edu.pe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("A domain of another university is refused")
    void aDomainOfAnotherUniversityIsRefused() throws Exception {

        String suspendedCode = people.aUniversity("D");
        Tenant suspended = tenants.findByCode(suspendedCode).orElseThrow();
        suspended.suspend(NOW);
        tenants.save(suspended);

        List<String> before = domainsOf(code);
        List<String> suspendedBefore = domainsOf(suspendedCode);

        // Active or not, a domain belongs to one university only, in whatever case it is written.
        replaceDomains(administrator, code, domains("fresh-" + domainOf(code), domainOf(suspendedCode).toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        replaceDomains(administrator, suspendedCode, domains(domainOf(code)))
                .andExpect(status().isConflict());

        assertThat(domainsOf(code)).isEqualTo(before);
        assertThat(domainsOf(suspendedCode)).isEqualTo(suspendedBefore);

        // Keeping its own domain is not a conflict.
        replaceDomains(administrator, code, domains(domainOf(code), "also-" + domainOf(code)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Two requests for the same domain end with one refusal")
    void twoRequestsForTheSameDomainEndWithOneRefusal() throws Exception {

        String other = people.aUniversity("D");
        String contested = "contested-" + domainOf(code);

        List<Integer> statuses = raceOf(administrator, code, other, domains(contested));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from identity.tenants where ? = any(email_domains)",
                                Integer.class,
                                contested))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Only the administrator edits the email domains")
    void onlyTheAdministratorEditsTheEmailDomains() throws Exception {

        List<String> before = domainsOf(code);
        String body = domains("taken-over.edu.pe");

        replaceDomains(coordinator.token(), code, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        replaceDomains(student.token(), code, body).andExpect(status().isForbidden());
        mockMvc.perform(
                        put("/api/v1/admin/universities/" + code + "/email-domains")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(domainsOf(code)).isEqualTo(before);
    }

    /** Sends the same body for both universities, released together by a barrier. */
    private List<Integer> raceOf(String token, String first, String second, String body) throws Exception {
        CyclicBarrier bothReady = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (String university : List.of(first, second)) {
                answers.add(
                        pool.submit(
                                () -> {
                                    bothReady.await();
                                    try {
                                        return replaceDomains(token, university, body)
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
