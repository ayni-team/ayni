package pe.ayni.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.domain.model.Tenant;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;

/**
 * US39 over HTTP against a real PostgreSQL: the scenario of {@code
 * features/US39-university-space.feature} that is this module's, with the same display name, and
 * the refusals it does not name. Nobody is signed in: the endpoint is public.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class UniversityBrandingAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IdentityTestDatabase.INSTANCE;

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private UserSessionRepository userSessions;
    @Autowired private AccessTokenGenerator tokens;

    private SignedInPeople people;
    private String code;
    private Person student;

    /** A university with a logo and colours, and a student of it who has an account. */
    @BeforeEach
    void aUniversityWithItsIdentityAndAStudent() {
        people = new SignedInPeople(tenants, users, userSessions, tokens);
        code = people.aUniversity("B");
        Tenant tenant = tenants.findByCode(code).orElseThrow();
        tenant.updateIdentity("https://cdn.ayni.pe/" + code + ".png", "#003A70", "#F5F5F5", NOW);
        tenants.save(tenant);
        student = people.aStudent(code);
    }

    private String domain() {
        return code.toLowerCase() + ".edu.pe";
    }

    private ResultActions identify(String email) throws Exception {
        return mockMvc.perform(get("/api/v1/access/university").param("email", email));
    }

    private String bodyOf(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Identification of the university")
    void identificationOfTheUniversity() throws Exception {

        identify("someone.new@" + domain())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("University " + code))
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.ayni.pe/" + code + ".png"))
                .andExpect(jsonPath("$.primaryColor").value("#003A70"))
                .andExpect(jsonPath("$.secondaryColor").value("#F5F5F5"));

        // Only the identity travels: no domains, grade or time zone, and nothing about people.
        String body = bodyOf(identify("someone.new@" + domain()));
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$").keySet())
                .containsExactlyInAnyOrder("code", "name", "logoUrl", "primaryColor", "secondaryColor");

        // The address of a person with an account, the one of a stranger and the same address in
        // capitals are answered alike: only the domain is looked at.
        String withAccount = bodyOf(identify(student.id() + "@" + domain()));
        assertThat(withAccount).isEqualTo(body);
        assertThat(bodyOf(identify("SOMEONE.NEW@" + domain().toUpperCase()))).isEqualTo(body);
    }

    @Test
    @DisplayName("A domain that no university claims is answered as not found")
    void aDomainThatNoUniversityClaimsIsNotFound() throws Exception {

        identify("student@no-university-claims-this.edu.pe")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/access/university"));
    }

    @Test
    @DisplayName("A suspended university is answered as one that is not affiliated")
    void aSuspendedUniversityIsAnsweredAsNotAffiliated() throws Exception {

        String unaffiliated =
                JsonPath.read(
                        bodyOf(identify("student@not-affiliated.edu.pe").andExpect(status().isNotFound())),
                        "$.message");

        Tenant tenant = tenants.findByCode(code).orElseThrow();
        tenant.suspend(NOW);
        tenants.save(tenant);

        identify("student@" + domain())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(unaffiliated));
    }

    @Test
    @DisplayName("An email that is not an address is answered as a bad request")
    void anEmailThatIsNotAnAddressIsABadRequest() throws Exception {

        for (String malformed :
                new String[] {"", "   ", "no-at-sign", "@" + domain(), "someone@", "a@b@" + domain(), "some one@" + domain()}) {
            identify(malformed)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").exists());
        }

        mockMvc.perform(get("/api/v1/access/university"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("email is required"));
    }
}
