package pe.ayni.reputation;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Reading back what a participant registered, over HTTP against a real PostgreSQL with real session
 * tokens (TS03): the four scenarios of {@code features/US32-rate-session.feature} that show a
 * rating, with the same names, plus what the endpoint refuses.
 *
 * <p>The window of every session is opened by publishing {@code SessionCompleted}, as sessions
 * does, and every rating is registered over HTTP. Every test signs in people of a university of its
 * own. Skills is mocked, as in {@code TutorReputationAcceptanceTest}, so both classes share one
 * application context.
 */
@SpringBootTest(properties = "ayni.test.identity-headers=false")
@AutoConfigureMockMvc
class RegisteredRatingAcceptanceTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = ReputationTestDatabase.INSTANCE;

    private static final String STUDENT_RATING =
            "{\"stars\":5,\"tags\":[\"HELPFUL\",\"CLEAR\"],\"comment\":\"Great session\"}";
    private static final String TUTOR_ASSESSMENT =
            "{\"wasPunctual\":true,\"connectionOk\":false,\"sessionFlowed\":true,"
                    + "\"comment\":\"The call dropped twice\"}";

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

    private SignedInPeople people() {
        return new SignedInPeople(tenants, users, userSessions, tokens);
    }

    /** A tutor, a student and a session of Calculus I between them that completed. */
    private record CompletedSession(Person tutor, Person student, UUID sessionId) {}

    private CompletedSession aCompletedSession() {
        SignedInPeople people = people();
        String tenant = people.aUniversity("R");
        Person tutor = people.aStudent(tenant);
        Person student = people.aStudent(tenant);
        UUID sessionId =
                CompletedSessions.complete(
                        events, transactions, jdbc, tenant, tutor.id(), student.id(), calculus);
        return new CompletedSession(tutor, student, sessionId);
    }

    private ResultActions rate(UUID sessionId, Person person, String body) throws Exception {
        return mockMvc.perform(
                post("/api/v1/sessions/{id}/rating", sessionId)
                        .header("Authorization", "Bearer " + person.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions registered(UUID sessionId, Person person) throws Exception {
        return mockMvc.perform(
                get("/api/v1/sessions/{id}/rating", sessionId)
                        .header("Authorization", "Bearer " + person.token()));
    }

    private String reputationOf(Person tutor) throws Exception {
        return mockMvc.perform(
                        get("/api/v1/tutor/reputation")
                                .header("Authorization", "Bearer " + tutor.token()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("A student sees the rating they registered")
    void aStudentSeesTheRatingTheyRegistered() throws Exception {
        CompletedSession session = aCompletedSession();
        // Given the student already rated the session
        rate(session.sessionId(), session.student(), STUDENT_RATING).andExpect(status().isCreated());

        // When the student tries to rate it again
        rate(session.sessionId(), session.student(), "{\"stars\":1}").andExpect(status().isConflict());

        // Then the system shows the rating that was registered instead of a new one
        registered(session.sessionId(), session.student())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rated").value(true))
                .andExpect(jsonPath("$.stars").value(5))
                .andExpect(jsonPath("$.tags[0]").value("CLEAR"))
                .andExpect(jsonPath("$.tags[1]").value("HELPFUL"))
                .andExpect(jsonPath("$.comment").value("Great session"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.wasPunctual").doesNotExist())
                .andExpect(jsonPath("$.connectionOk").doesNotExist())
                .andExpect(jsonPath("$.sessionFlowed").doesNotExist());
    }

    @Test
    @DisplayName("The tutor does not learn who rated them")
    void theTutorDoesNotLearnWhoRatedThem() throws Exception {
        CompletedSession session = aCompletedSession();
        when(skills.enabledSkillsOf(session.tutor().id())).thenReturn(List.of(calculus));
        when(skills.requireItem(calculus))
                .thenReturn(new CatalogItemView(calculus, CatalogScope.UNIVERSITY, "Calculus I", "MA101"));
        // Given the student rated the session
        rate(session.sessionId(), session.student(), STUDENT_RATING).andExpect(status().isCreated());

        // When the tutor looks at the rating
        String ofTheSession =
                registered(session.sessionId(), session.tutor())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.rated").value(false))
                        .andExpect(jsonPath("$.stars").doesNotExist())
                        .andExpect(jsonPath("$.tags").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String reputation = reputationOf(session.tutor());

        // Then they see the stars and the tags without who left them
        assertThat(reputation)
                .contains("\"provisionalAverage\":5.00")
                .contains("{\"tag\":\"CLEAR\",\"count\":1}")
                .contains("{\"tag\":\"HELPFUL\",\"count\":1}");
        assertThat(ofTheSession + reputation)
                .doesNotContain(session.student().id().toString())
                .doesNotContain("Great session")
                .doesNotContain(session.sessionId().toString());
    }

    @Test
    @DisplayName("The student does not see the assessment of the tutor")
    void theStudentDoesNotSeeTheAssessmentOfTheTutor() throws Exception {
        CompletedSession session = aCompletedSession();
        // Given the tutor assessed the student
        rate(session.sessionId(), session.tutor(), TUTOR_ASSESSMENT).andExpect(status().isCreated());

        // When the student looks at the session
        String answer =
                registered(session.sessionId(), session.student())
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // Then they do not see what the tutor left about them
        assertThat(answer).isEqualTo("{\"rated\":false}");
    }

    @Test
    @DisplayName("A tutor sees the assessment they registered")
    void aTutorSeesTheAssessmentTheyRegistered() throws Exception {
        CompletedSession session = aCompletedSession();
        // Given the tutor already assessed the student
        rate(session.sessionId(), session.tutor(), TUTOR_ASSESSMENT).andExpect(status().isCreated());

        // When the tutor tries to assess them again
        rate(
                        session.sessionId(),
                        session.tutor(),
                        "{\"wasPunctual\":false,\"connectionOk\":false,\"sessionFlowed\":false}")
                .andExpect(status().isConflict());

        // Then the system shows the assessment that was registered instead of a new one
        registered(session.sessionId(), session.tutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rated").value(true))
                .andExpect(jsonPath("$.wasPunctual").value(true))
                .andExpect(jsonPath("$.connectionOk").value(false))
                .andExpect(jsonPath("$.sessionFlowed").value(true))
                .andExpect(jsonPath("$.comment").value("The call dropped twice"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.stars").doesNotExist())
                .andExpect(jsonPath("$.tags").doesNotExist());
    }

    @Test
    @DisplayName("Somebody who was not in the session cannot read its rating")
    void somebodyWhoWasNotInTheSessionCannotReadItsRating() throws Exception {
        CompletedSession session = aCompletedSession();
        rate(session.sessionId(), session.student(), STUDENT_RATING).andExpect(status().isCreated());
        Person stranger = people().aStudent(session.student().tenantId());

        registered(session.sessionId(), stranger)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/v1/sessions/" + session.sessionId() + "/rating"));
    }

    @Test
    @DisplayName("A session that did not complete has no rating to read")
    void aSessionThatDidNotCompleteHasNoRatingToRead() throws Exception {
        CompletedSession session = aCompletedSession();

        registered(UUID.randomUUID(), session.student())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Another university's session is not found when reading its rating")
    void anotherUniversitysSessionIsNotFoundWhenReadingItsRating() throws Exception {
        CompletedSession elsewhere = aCompletedSession();
        rate(elsewhere.sessionId(), elsewhere.student(), STUDENT_RATING)
                .andExpect(status().isCreated());
        SignedInPeople people = people();
        Person studentOfAnother = people.aStudent(people.aUniversity("R"));

        registered(elsewhere.sessionId(), studentOfAnother)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("A participant who has not rated yet reads that nothing was registered")
    void aParticipantWhoHasNotRatedYetReadsThatNothingWasRegistered() throws Exception {
        CompletedSession session = aCompletedSession();

        registered(session.sessionId(), session.student())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rated").value(false));
        registered(session.sessionId(), session.tutor())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rated").value(false));
    }
}
