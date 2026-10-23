package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.PlatformAdmin;
import pe.ayni.identity.domain.model.PlatformAdminStatus;
import pe.ayni.identity.domain.model.UserSession;
import pe.ayni.identity.infrastructure.PlatformAdminRepository;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.shared.events.CreditPolicyChanged;
import pe.ayni.shared.events.UniversityIdentityChanged;

/**
 * US52 and US50, the audit of a change of configuration, over HTTP with real session tokens against
 * a real database: changing the credit policy or the identity of a university leaves an entry that
 * the coordinator reads in {@code GET /api/v1/coordinator/audit-log}.
 *
 * <p>One test per scenario of {@code features/US52-credit-policy.feature} and {@code
 * features/US50-university-identity.feature} that names this class, with the same display name.
 * Every entry is produced by the real use case through its endpoint, and audit hears the real event;
 * the log is read through the endpoint of the story, as the coordinator does.
 *
 * <p>The clock stands at 15:00 UTC of the 20th.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
@Import(ConfigurationChangesAuditAcceptanceTest.FixedClock.class)
class ConfigurationChangesAuditAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final String LOG = "/api/v1/coordinator/audit-log";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private UserSessionRepository userSessions;
    @Autowired private PlatformAdminRepository admins;
    @Autowired private AccessTokenGenerator tokens;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;

    private SignedInPeople people;
    private String code;
    private Person coordinator;
    private UUID administratorId;
    private String administrator;

    /** Background: a university with a baseline policy and a coordinator, and a platform administrator. */
    @BeforeEach
    void aUniversityWithItsCoordinatorAndAnAdministrator() {
        people = new SignedInPeople(tenants, users, userSessions, tokens);
        code = people.aUniversity("C");
        coordinator = people.aCoordinator(code);
        jdbc.update(
                "insert into identity.credit_policies (id, tenant_id, kind, credits_amount, validity_days,"
                        + " valid_from, created_by_user, created_at)"
                        + " values (?, ?, 'BASELINE', 5, 30, '2026-09-01', ?, ?::timestamptz)",
                UUID.randomUUID(),
                code,
                coordinator.id(),
                "2026-09-01T10:00:00Z");
        administratorId = UUID.randomUUID();
        admins.save(
                new PlatformAdmin(
                        administratorId,
                        "admin-" + administratorId + "@ayni.test",
                        "Admin",
                        PlatformAdminStatus.ACTIVE,
                        NOW));
        administrator = tokens.generate().rawToken();
        userSessions.save(
                UserSession.forPlatformAdmin(
                        UUID.randomUUID(),
                        administratorId,
                        tokens.hash(administrator),
                        NOW.plus(Duration.ofDays(30)),
                        NOW));
    }

    private ResultActions changePolicy(Person who, int credits, int validityDays) throws Exception {
        return mockMvc.perform(
                put("/api/v1/coordinator/credit-policy")
                        .header("Authorization", "Bearer " + who.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditsAmount\": %d, \"validityDays\": %d}".formatted(credits, validityDays)));
    }

    private ResultActions changeIdentity(Person who, String primaryColor) throws Exception {
        return mockMvc.perform(
                put("/api/v1/coordinator/university-identity")
                        .header("Authorization", "Bearer " + who.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"logoUrl\": \"https://cdn.ayni.pe/%s.png\", \"primaryColor\": \"%s\","
                                                .formatted(code.toLowerCase(), primaryColor)
                                        + " \"secondaryColor\": \"#F5F5F5\"}"));
    }

    private ResultActions replaceDomains(String university, String domain) throws Exception {
        return mockMvc.perform(
                put("/api/v1/admin/universities/" + university + "/email-domains")
                        .header("Authorization", "Bearer " + administrator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailDomains\": [\"" + domain + "\"]}"));
    }

    /** What the coordinator reads from the log of their university about one kind of resource. */
    private List<Map<String, Object>> logOf(Person reader, String resourceType) {
        try {
            String body =
                    mockMvc.perform(
                                    get(LOG + "?resourceType=" + resourceType)
                                            .header("Authorization", "Bearer " + reader.token()))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            return JsonPath.read(body, "$.items");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Audit hears the event after the publisher commits, on another thread: wait for the entry. */
    private Map<String, Object> theOnlyEntry(Person reader, String resourceType) {
        await().atMost(Duration.ofSeconds(10)).until(() -> !logOf(reader, resourceType).isEmpty());
        List<Map<String, Object>> found = logOf(reader, resourceType);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    /** Nothing else arrives: the log stays at the same number of entries for a while. */
    private void theLogStaysAt(Person reader, String resourceType, int expected) {
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(10))
                .until(() -> logOf(reader, resourceType).size() == expected);
    }

    @Test
    @DisplayName("A credit policy change is recorded in the audit log")
    void aCreditPolicyChangeIsRecordedInTheAuditLog() throws Exception {

        String changed =
                changePolicy(coordinator, 8, 45).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID policy = UUID.fromString(JsonPath.read(changed, "$.id"));

        Map<String, Object> entry = theOnlyEntry(coordinator, "CREDIT_POLICY");

        assertThat(entry.get("action")).isEqualTo("CREDIT_POLICY_CHANGED");
        assertThat(entry.get("actorId")).isEqualTo(coordinator.id().toString());
        assertThat(entry.get("actorRole")).isEqualTo("COORDINATOR");
        assertThat(entry.get("resourceType")).isEqualTo("CREDIT_POLICY");
        assertThat(entry.get("resourceId")).isEqualTo(policy.toString());
        assertThat(entry.get("occurredAt").toString()).startsWith("2026-10-20T15:00:00");
        assertThat(entry.get("details"))
                .isEqualTo(Map.of("kind", "BASELINE", "creditsAmount", "8", "validityDays", "45"));
    }

    @Test
    @DisplayName("A rejected credit policy change leaves no entry")
    void aRejectedCreditPolicyChangeLeavesNoEntry() throws Exception {

        changePolicy(coordinator, 0, 45).andExpect(status().isBadRequest());
        changePolicy(people.aStudent(code), 8, 45).andExpect(status().isForbidden());

        theLogStaysAt(coordinator, "CREDIT_POLICY", 0);

        // A change that is accepted afterwards is the only entry.
        changePolicy(coordinator, 8, 45).andExpect(status().isOk());
        assertThat(theOnlyEntry(coordinator, "CREDIT_POLICY").get("details"))
                .isEqualTo(Map.of("kind", "BASELINE", "creditsAmount", "8", "validityDays", "45"));
    }

    @Test
    @DisplayName("The entry of a credit policy change is not seen by another university")
    void theEntryOfACreditPolicyChangeIsNotSeenByAnotherUniversity() throws Exception {

        Person otherCoordinator = people.aCoordinator(people.aUniversity("O"));

        changePolicy(coordinator, 8, 45).andExpect(status().isOk());
        theOnlyEntry(coordinator, "CREDIT_POLICY");

        assertThat(logOf(otherCoordinator, "CREDIT_POLICY")).isEmpty();
        assertThat(logOf(otherCoordinator, "UNIVERSITY")).isEmpty();
    }

    @Test
    @DisplayName("The same credit policy change delivered twice is recorded once")
    void theSameCreditPolicyChangeDeliveredTwiceIsRecordedOnce() throws Exception {

        String changed =
                changePolicy(coordinator, 8, 45).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID policy = UUID.fromString(JsonPath.read(changed, "$.id"));
        theOnlyEntry(coordinator, "CREDIT_POLICY");

        CreditPolicyChanged again = new CreditPolicyChanged(code, policy, "BASELINE", 8, 45, coordinator.id(), NOW);
        transactions.executeWithoutResult(status -> events.publishEvent(again));
        transactions.executeWithoutResult(status -> events.publishEvent(again));

        theLogStaysAt(coordinator, "CREDIT_POLICY", 1);

        // Another change is another policy, and so another entry.
        changePolicy(coordinator, 9, 60).andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(10)).until(() -> logOf(coordinator, "CREDIT_POLICY").size() == 2);
    }

    @Test
    @DisplayName("A change of the university identity is recorded in the audit log")
    void aChangeOfTheUniversityIdentityIsRecordedInTheAuditLog() throws Exception {

        Person otherCoordinator = people.aCoordinator(people.aUniversity("O"));

        changeIdentity(coordinator, "#003A70").andExpect(status().isOk());

        Map<String, Object> entry = theOnlyEntry(coordinator, "UNIVERSITY");

        assertThat(entry.get("action")).isEqualTo("UNIVERSITY_IDENTITY_CHANGED");
        assertThat(entry.get("actorId")).isEqualTo(coordinator.id().toString());
        assertThat(entry.get("actorRole")).isEqualTo("COORDINATOR");
        assertThat(entry.get("resourceType")).isEqualTo("UNIVERSITY");
        assertThat(entry.get("occurredAt").toString()).startsWith("2026-10-20T15:00:00");
        // It says that the identity changed, never the logo or the colours.
        assertThat((Map<?, ?>) entry.get("details")).isEmpty();
        assertThat(entry.toString()).doesNotContain("003A70").doesNotContain("cdn.ayni.pe");

        // Another university does not see it.
        assertThat(logOf(otherCoordinator, "UNIVERSITY")).isEmpty();
    }

    @Test
    @DisplayName("A change of the email domains is recorded in the audit log")
    void aChangeOfTheEmailDomainsIsRecordedInTheAuditLog() throws Exception {

        replaceDomains(code, "new-" + code.toLowerCase() + ".edu.pe").andExpect(status().isOk());

        Map<String, Object> entry = theOnlyEntry(coordinator, "UNIVERSITY");

        // The administrator has no university: the entry belongs to the one that was edited, and
        // it names the administrator as who acted.
        assertThat(entry.get("action")).isEqualTo("UNIVERSITY_IDENTITY_CHANGED");
        assertThat(entry.get("actorId")).isEqualTo(administratorId.toString());
        assertThat(entry.get("actorRole")).isEqualTo("PLATFORM_ADMIN");
        assertThat((Map<?, ?>) entry.get("details")).isEmpty();
        assertThat(entry.toString()).doesNotContain(".edu.pe");
    }

    @Test
    @DisplayName("A rejected change of the university identity leaves no entry")
    void aRejectedChangeOfTheUniversityIdentityLeavesNoEntry() throws Exception {

        changeIdentity(coordinator, "#".repeat(20)).andExpect(status().isBadRequest());
        changeIdentity(people.aStudent(code), "#003A70").andExpect(status().isForbidden());
        // A domain another university holds is refused as a conflict.
        String other = people.aUniversity("O");
        replaceDomains(code, other.toLowerCase() + ".edu.pe").andExpect(status().isConflict());

        theLogStaysAt(coordinator, "UNIVERSITY", 0);
    }

    @Test
    @DisplayName("The same identity change delivered twice is recorded once")
    void theSameIdentityChangeDeliveredTwiceIsRecordedOnce() throws Exception {

        changeIdentity(coordinator, "#003A70").andExpect(status().isOk());
        theOnlyEntry(coordinator, "UNIVERSITY");

        UniversityIdentityChanged again = new UniversityIdentityChanged(code, coordinator.id(), NOW);
        transactions.executeWithoutResult(status -> events.publishEvent(again));
        transactions.executeWithoutResult(status -> events.publishEvent(again));

        theLogStaysAt(coordinator, "UNIVERSITY", 1);
    }
}
