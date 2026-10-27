package pe.ayni.reputation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import pe.ayni.config.SignedInPeople;
import pe.ayni.config.SignedInPeople.Person;
import pe.ayni.identity.application.AccessTokenGenerator;
import pe.ayni.identity.infrastructure.TenantRepository;
import pe.ayni.identity.infrastructure.UserRepository;
import pe.ayni.identity.infrastructure.UserSessionRepository;
import pe.ayni.skills.CatalogItemView;
import pe.ayni.skills.CatalogScope;
import pe.ayni.skills.SkillsApi;

/**
 * US33 over HTTP against a real PostgreSQL with real session tokens (TS03), one test per scenario
 * of {@code features/US33-tutor-reputation.feature}, with the same name.
 *
 * <p>Sessions complete by publishing {@code SessionCompleted}, as sessions does, and students rate
 * them over HTTP, so the standing and the tags are the ones production builds. Skills is the seam,
 * mocked with the skills the tutor is enabled for and their names. Every test signs in people of a
 * university of its own and uses courses of its own.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class TutorReputationAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = ReputationTestDatabase.INSTANCE;

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private UserSessionRepository userSessions;
    @Autowired private AccessTokenGenerator tokens;

    @MockitoBean private SkillsApi skills;

    private final UUID calculus = UUID.randomUUID();
    private final UUID physics = UUID.randomUUID();

    private SignedInPeople people() {
        return new SignedInPeople(tenants, users, userSessions, tokens);
    }

    /** A tutor of a new university, enabled for these items, which skills names as given. */
    private Person aTutorEnabledFor(String tenant, UUID... items) {
        Person tutor = people().aStudent(tenant);
        when(skills.enabledSkillsOf(tutor.id())).thenReturn(List.of(items));
        named(calculus, "Calculus I");
        named(physics, "Physics I");
        return tutor;
    }

    private void named(UUID item, String name) {
        when(skills.requireItem(item))
                .thenReturn(new CatalogItemView(item, CatalogScope.UNIVERSITY, name, null));
    }

    /** A session of the tutor in that skill completes and a new student of it rates it. */
    private void ratedSession(Person tutor, UUID item, String rating) throws Exception {
        Person student = people().aStudent(tutor.tenantId());
        UUID sessionId =
                CompletedSessions.complete(
                        events, transactions, jdbc, tutor.tenantId(), tutor.id(), student.id(), item);
        mockMvc.perform(
                        post("/api/v1/sessions/{id}/rating", sessionId)
                                .header("Authorization", "Bearer " + student.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(rating))
                .andExpect(status().isCreated());
    }

    private static String stars(int stars, String... tags) {
        StringBuilder body = new StringBuilder("{\"stars\":").append(stars).append(",\"tags\":[");
        for (int i = 0; i < tags.length; i++) {
            body.append(i == 0 ? "" : ",").append('"').append(tags[i]).append('"');
        }
        return body.append("]}").toString();
    }

    private ResultActions reputationOf(Person tutor) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/tutor/reputation")
                                .header("Authorization", "Bearer " + tutor.token()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A summary of my reputation")
    void aSummaryOfMyReputation() throws Exception {
        Person tutor = aTutorEnabledFor(people().aUniversity("R"), calculus);
        // Given I taught sessions that students rated
        ratedSession(tutor, calculus, stars(5, "CLEAR", "PATIENT"));
        ratedSession(tutor, calculus, stars(4, "CLEAR"));
        ratedSession(tutor, calculus, stars(5, "CLEAR", "PATIENT", "HELPFUL"));

        // When I look at my reputation as a tutor
        // Then I see my average, how many ratings I received and the tags students gave me most
        reputationOf(tutor)
                .andExpect(jsonPath("$.minimumAverage").value(3.0))
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].catalogItemId").value(calculus.toString()))
                .andExpect(jsonPath("$.skills[0].name").value("Calculus I"))
                .andExpect(jsonPath("$.skills[0].sessionsTaught").value(3))
                .andExpect(jsonPath("$.skills[0].ratingsCount").value(3))
                .andExpect(jsonPath("$.skills[0].averageStars").value(4.67))
                .andExpect(jsonPath("$.skills[0].provisionalAverage").value(nullValue()))
                .andExpect(jsonPath("$.skills[0].ratingsMissingForAverage").value(0))
                .andExpect(jsonPath("$.skills[0].topTags.length()").value(3))
                .andExpect(jsonPath("$.skills[0].topTags[0].tag").value("CLEAR"))
                .andExpect(jsonPath("$.skills[0].topTags[0].count").value(3))
                .andExpect(jsonPath("$.skills[0].topTags[1].tag").value("PATIENT"))
                .andExpect(jsonPath("$.skills[0].topTags[1].count").value(2))
                .andExpect(jsonPath("$.skills[0].topTags[2].tag").value("HELPFUL"))
                .andExpect(jsonPath("$.skills[0].topTags[2].count").value(1))
                .andExpect(jsonPath("$.skills[0].atRisk").value(false));
    }

    @Test
    @DisplayName("My reputation in each skill")
    void myReputationInEachSkill() throws Exception {
        Person tutor = aTutorEnabledFor(people().aUniversity("R"), calculus, physics);
        // Given I offer several skills
        ratedSession(tutor, calculus, stars(5, "CLEAR"));
        ratedSession(tutor, calculus, stars(5, "CLEAR"));
        ratedSession(tutor, calculus, stars(4, "CLEAR"));
        ratedSession(tutor, physics, stars(2, "RUSHED"));

        // When I look at my reputation
        // Then I see how I am rated in each one separately
        reputationOf(tutor)
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0].name").value("Calculus I"))
                .andExpect(jsonPath("$.skills[0].ratingsCount").value(3))
                .andExpect(jsonPath("$.skills[0].averageStars").value(4.67))
                .andExpect(jsonPath("$.skills[0].topTags.length()").value(1))
                .andExpect(jsonPath("$.skills[0].topTags[0].tag").value("CLEAR"))
                .andExpect(jsonPath("$.skills[0].topTags[0].count").value(3))
                .andExpect(jsonPath("$.skills[1].name").value("Physics I"))
                .andExpect(jsonPath("$.skills[1].sessionsTaught").value(1))
                .andExpect(jsonPath("$.skills[1].ratingsCount").value(1))
                .andExpect(jsonPath("$.skills[1].averageStars").value(nullValue()))
                .andExpect(jsonPath("$.skills[1].topTags.length()").value(1))
                .andExpect(jsonPath("$.skills[1].topTags[0].tag").value("RUSHED"));
    }

    @Test
    @DisplayName("A reputation at risk is marked")
    void aReputationAtRiskIsMarked() throws Exception {
        Person tutor = aTutorEnabledFor(people().aUniversity("R"), calculus, physics);
        // Given my average in a skill falls below the minimum required
        ratedSession(tutor, physics, stars(2));
        ratedSession(tutor, physics, stars(3));
        ratedSession(tutor, physics, stars(2));
        ratedSession(tutor, calculus, stars(3));
        ratedSession(tutor, calculus, stars(3));
        ratedSession(tutor, calculus, stars(3));

        // When I look at my reputation
        // Then that skill is marked at risk and the others are not
        reputationOf(tutor)
                .andExpect(jsonPath("$.skills[0].name").value("Calculus I"))
                .andExpect(jsonPath("$.skills[0].averageStars").value(3.0))
                .andExpect(jsonPath("$.skills[0].atRisk").value(false))
                .andExpect(jsonPath("$.skills[1].name").value("Physics I"))
                .andExpect(jsonPath("$.skills[1].averageStars").value(2.33))
                .andExpect(jsonPath("$.skills[1].atRisk").value(true));

        // A mark only: reading it again changes nothing, and nothing was written about it. The
        // notices that closing the rated sessions wrote are not about the mark, so what is checked
        // is that reading adds none.
        int noticesBefore = noticesOf(tutor);
        reputationOf(tutor).andExpect(jsonPath("$.skills[1].atRisk").value(true));
        assertThat(noticesOf(tutor)).isEqualTo(noticesBefore);
    }

    private int noticesOf(Person person) {
        return jdbc.queryForObject(
                "select count(*) from notifications.notifications where recipient_id = ?",
                Integer.class,
                person.id());
    }

    @Test
    @DisplayName("A tutor without enough ratings")
    void aTutorWithoutEnoughRatings() throws Exception {
        Person tutor = aTutorEnabledFor(people().aUniversity("R"), calculus);
        // Given I do not have the minimum number of ratings yet
        ratedSession(tutor, calculus, stars(4, "CLEAR"));

        // When I look at my reputation
        // Then I see how many ratings are missing before an average is calculated, and a
        // provisional average instead
        reputationOf(tutor)
                .andExpect(jsonPath("$.skills[0].ratingsCount").value(1))
                .andExpect(jsonPath("$.skills[0].averageStars").value(nullValue()))
                .andExpect(jsonPath("$.skills[0].provisionalAverage").value(4.0))
                .andExpect(jsonPath("$.skills[0].ratingsMissingForAverage").value(2))
                .andExpect(jsonPath("$.skills[0].atRisk").value(false));
    }

    @Test
    @DisplayName("Only my own ratings count in my reputation")
    void onlyMyOwnRatingsCountInMyReputation() throws Exception {
        String tenant = people().aUniversity("R");
        Person tutor = aTutorEnabledFor(tenant, calculus);
        ratedSession(tutor, calculus, stars(5, "CLEAR"));
        // Given another tutor of my university and a tutor of another university were rated in
        // the same course
        Person colleague = aTutorEnabledFor(tenant, calculus);
        ratedSession(colleague, calculus, stars(1, "LATE"));
        Person elsewhere = aTutorEnabledFor(people().aUniversity("R"), calculus);
        ratedSession(elsewhere, calculus, stars(1, "RUDE"));

        // When I look at my reputation
        String answer =
                reputationOf(tutor)
                        .andExpect(jsonPath("$.skills.length()").value(1))
                        .andExpect(jsonPath("$.skills[0].sessionsTaught").value(1))
                        .andExpect(jsonPath("$.skills[0].ratingsCount").value(1))
                        .andExpect(jsonPath("$.skills[0].provisionalAverage").value(5.0))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Then none of their ratings or tags count in mine
        assertThat(answer).contains("CLEAR").doesNotContain("LATE").doesNotContain("RUDE");
    }

    @Test
    @DisplayName("A skill I am enabled for and never taught has nothing counted")
    void aSkillIAmEnabledForAndNeverTaughtHasNothingCounted() throws Exception {
        Person tutor = aTutorEnabledFor(people().aUniversity("R"), physics);

        // Given I am enabled for "Physics I" and never taught it
        // When I look at my reputation
        // Then "Physics I" appears with no sessions, no ratings and three ratings missing
        reputationOf(tutor)
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].catalogItemId").value(physics.toString()))
                .andExpect(jsonPath("$.skills[0].name").value("Physics I"))
                .andExpect(jsonPath("$.skills[0].sessionsTaught").value(0))
                .andExpect(jsonPath("$.skills[0].ratingsCount").value(0))
                .andExpect(jsonPath("$.skills[0].averageStars").value(nullValue()))
                .andExpect(jsonPath("$.skills[0].provisionalAverage").value(nullValue()))
                .andExpect(jsonPath("$.skills[0].ratingsMissingForAverage").value(3))
                .andExpect(jsonPath("$.skills[0].topTags.length()").value(0))
                .andExpect(jsonPath("$.skills[0].atRisk").value(false));
    }
}
